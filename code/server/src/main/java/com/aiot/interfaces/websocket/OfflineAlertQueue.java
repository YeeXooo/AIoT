package com.aiot.interfaces.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 离线告警队列。
 * <p>
 * 当家属 APP 或车队大屏不在线时，将告警消息暂存到内存队列，
 * 待客户端重新连接后按序补推。消息按 accountId 分组存储，
 * 每个账户最多保留 {@code maxOfflineAlertsPerReconnect} 条消息，
 * 超过保留天数的消息自动丢弃。
 * </p>
 * <p>
 * 设计依据：docs/ood_interface.md §3、WebSocketProperties
 * </p>
 */
@Component
public class OfflineAlertQueue {

    private static final Logger log = LoggerFactory.getLogger(OfflineAlertQueue.class);

    private final WebSocketProperties properties;

    /** accountId → 离线消息队列 */
    private final Map<String, Deque<QueuedAlert>> queues = new ConcurrentHashMap<>();

    public OfflineAlertQueue(WebSocketProperties properties) {
        this.properties = properties;
    }

    /**
     * 将告警加入离线队列。
     *
     * @param accountId 目标账户标识
     * @param payload   告警消息负载（JSON 字符串）
     */
    public void enqueue(String accountId, String payload) {
        Deque<QueuedAlert> queue = queues.computeIfAbsent(accountId, k -> new ConcurrentLinkedDeque<>());

        // 保留上限：超过最大数量则丢弃最旧的消息
        while (queue.size() >= properties.getMaxOfflineAlertsPerReconnect()) {
            queue.pollFirst();
        }

        queue.addLast(new QueuedAlert(payload, Instant.now()));
    }

    /**
     * 取出并清空指定账户的离线消息。
     * 自动丢弃超过保留天数的过时消息。
     *
     * @return 按时间排序的离线消息列表（最旧在前），无消息时返回空列表
     */
    public List<String> drain(String accountId) {
        Deque<QueuedAlert> queue = queues.remove(accountId);
        if (queue == null || queue.isEmpty()) {
            return Collections.emptyList();
        }

        Instant cutoff = Instant.now().minusSeconds(
                properties.getOfflineMessageRetentionDays() * 86400L);

        List<String> result = new ArrayList<>();
        for (QueuedAlert alert : queue) {
            if (alert.enqueuedAt.isAfter(cutoff)) {
                result.add(alert.payload);
            }
        }

        log.info("离线消息补推: accountId={}, total={}, valid={}",
                accountId, queue.size(), result.size());
        return result;
    }

    /**
     * 获取某账户的离线消息数量（不清除）。
     */
    public int pendingCount(String accountId) {
        Deque<QueuedAlert> queue = queues.get(accountId);
        return queue == null ? 0 : queue.size();
    }

    /**
     * 获取全部待补推账户数。
     */
    public int totalPendingAccounts() {
        return queues.size();
    }

    private record QueuedAlert(String payload, Instant enqueuedAt) {}
}
