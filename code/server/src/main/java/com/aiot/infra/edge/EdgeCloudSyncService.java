package com.aiot.infra.edge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/**
 * 边缘-云端数据同步服务。
 * <p>
 * 协调边缘侧离线缓冲、MQTT 连接状态感知和批量重传：
 * <ul>
 *   <li>MQTT 在线时直接发送，失败后存入 SQLite 离线缓冲</li>
 *   <li>MQTT 恢复连接后自动批量重传缓冲消息</li>
 *   <li>基于 SHA-256 的消息幂等去重，避免重复投递</li>
 *   <li>定时清除过期缓冲消息</li>
 * </ul>
 * </p>
 * <p>
 * 设计依据：docs/ood_infrastructure.md §3.8、requirements.md §六 可靠性
 * </p>
 */
public class EdgeCloudSyncService {

    private static final Logger log = LoggerFactory.getLogger(EdgeCloudSyncService.class);

    private final EdgePersistenceService persistence;
    private final EdgeMqttClient mqttClient;
    private final EdgeProperties properties;
    private final ObjectMapper objectMapper;

    /** 已发送消息的哈希集合（滑动窗口去重） */
    private final Map<String, Long> sentHashes = new ConcurrentHashMap<>();

    /** 定时重传调度器 */
    private final ScheduledExecutorService retryExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "edge-sync-retry");
                t.setDaemon(true);
                return t;
            });

    /** 定时清理过期消息调度器 */
    private final ScheduledExecutorService purgeExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "edge-sync-purge");
                t.setDaemon(true);
                return t;
            });

    private volatile boolean started = false;

    /** 去重窗口大小 */
    private static final int DEDUP_MAX_SIZE = 10000;

    /** 去重条目过期（毫秒） */
    private static final long DEDUP_ENTRY_TTL_MS = 3600_000; // 1 小时

    public EdgeCloudSyncService(EdgePersistenceService persistence,
                                 EdgeMqttClient mqttClient,
                                 EdgeProperties properties,
                                 ObjectMapper objectMapper) {
        this.persistence = persistence;
        this.mqttClient = mqttClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 启动同步服务，注册 MQTT 连接状态监听并开始定时任务。
     */
    public void start() {
        if (started) return;
        started = true;

        // 初始化 SQLite
        persistence.init();

        // 监听 MQTT 连接状态
        mqttClient.onConnectionStateChange(connected -> {
            if (connected) {
                log.info("边缘 MQTT 已连接，触发缓冲消息重传");
                retryExecutor.execute(this::flushBuffer);
            }
        });

        // 定时重传（连接恢复后立即执行一次，之后按间隔执行）
        retryExecutor.scheduleAtFixedRate(
                this::flushBuffer,
                properties.getRetryIntervalSec(),
                properties.getRetryIntervalSec(),
                TimeUnit.SECONDS);

        // 定时清理过期消息
        purgeExecutor.scheduleAtFixedRate(
                persistence::purgeExpired,
                60, 3600, TimeUnit.SECONDS);

        // 启动 MQTT
        mqttClient.start();

        log.info("边缘-云端同步服务已启动: mode={}, deviceId={}",
                properties.getMode(), properties.getDeviceId());
    }

    /**
     * 停止同步服务。
     */
    public void stop() {
        started = false;
        retryExecutor.shutdownNow();
        purgeExecutor.shutdownNow();
        mqttClient.stop();
        persistence.shutdown();
        log.info("边缘-云端同步服务已停止");
    }

    /**
     * 发送消息（带离线缓冲保护）。
     * <p>
     * 调用顺序：MQTT 直接发送 → 失败则存入 SQLite → 连接恢复后自动重传。
     * 含幂等去重：相同 topic+payload 组合在去重窗口内只发送一次。
     * </p>
     *
     * @param topic   MQTT 主题
     * @param payload JSON 字符串负载
     * @param qos     QoS 等级
     */
    public void sendWithBuffer(String topic, String payload, int qos) {
        // 幂等去重
        String hash = hash(topic + "|" + payload);
        if (isDuplicate(hash)) {
            log.debug("消息幂等跳过: topic={}", topic);
            return;
        }
        sentHashes.put(hash, System.currentTimeMillis());
        trimDedupCache();

        // 直接发送
        if (mqttClient.isConnected()) {
            boolean ok = mqttClient.publish(topic, payload, qos);
            if (ok) return;
        }

        // 发送失败或未连接 → 离线缓冲
        Instant expiredAt = Instant.now().plusSeconds(
                properties.getBufferRetentionHours() * 3600L);
        persistence.saveOfflineMessage(topic, payload, qos, expiredAt);

        if (persistence.countPending() >= properties.getBufferMaxEntries()) {
            log.warn("离线缓冲已满 ({} 条)，最早的消息将被丢弃",
                    properties.getBufferMaxEntries());
            persistence.purgeExpired();
        }
    }

    /**
     * 获取当前缓冲待发送消息数。
     */
    public int pendingCount() {
        return persistence.countPending();
    }

    // ── 内部 ──

    /**
     * 批量重传缓冲消息。
     */
    private void flushBuffer() {
        if (!mqttClient.isConnected()) {
            log.debug("MQTT 未连接，跳过 flush");
            return;
        }

        int batchSize = properties.getBatchSize();
        var messages = persistence.fetchPendingMessages(batchSize);

        if (messages.isEmpty()) return;

        log.info("开始批量重传离线消息: {} 条", messages.size());
        int successCount = 0;
        int failCount = 0;

        for (var msg : messages) {
            try {
                boolean ok = mqttClient.publish(msg.topic(), msg.payload(), msg.qos());
                if (ok) {
                    persistence.markSent(msg.id());
                    successCount++;
                } else {
                    persistence.incrementRetry(msg.id());
                    failCount++;
                }
            } catch (Exception e) {
                log.error("重传失败: id={}, topic={}", msg.id(), msg.topic(), e);
                persistence.incrementRetry(msg.id());
                failCount++;
            }
        }

        log.info("批量重传完成: 成功={}, 失败={}", successCount, failCount);
    }

    private String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    private boolean isDuplicate(String hash) {
        Long timestamp = sentHashes.get(hash);
        if (timestamp == null) return false;
        return System.currentTimeMillis() - timestamp < DEDUP_ENTRY_TTL_MS;
    }

    private void trimDedupCache() {
        if (sentHashes.size() < DEDUP_MAX_SIZE) return;
        long cutoff = System.currentTimeMillis() - DEDUP_ENTRY_TTL_MS;
        sentHashes.entrySet().removeIf(e -> e.getValue() < cutoff);
    }
}
