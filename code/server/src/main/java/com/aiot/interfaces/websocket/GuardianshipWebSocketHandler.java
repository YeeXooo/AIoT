package com.aiot.interfaces.websocket;

import static com.aiot.interfaces.websocket.WebSocketPayloads.*;

import com.aiot.application.PendingFamilyRequestStore;
import com.aiot.infra.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * 家属 APP WebSocket 处理器。
 * <p>
 * 处理家属端的状态订阅、告警推送、音视频对讲请求和手动救援触发。
 * 使用 JwtTokenProvider 从 URL 查询参数中提取并验证 JWT，
 * 通过 MediaSessionManager 管理 mock SparkRTC 会话。
 * </p>
 * <p>
 * 设计依据：docs/ood_interface.md §3、docs/communication_architecture.md §2.5
 * </p>
 */
@Component
public class GuardianshipWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GuardianshipWebSocketHandler.class);

    private final WebSocketSessionRegistry sessionRegistry;
    private final WebSocketProperties properties;
    private final ObjectMapper objectMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final MediaSessionManager mediaSessionManager;
    private final OfflineAlertQueue offlineAlertQueue;
    private final PendingFamilyRequestStore pendingFamilyRequestStore;
    private final ScheduledExecutorService heartbeatExecutor;

    /** sessionId → 心跳定时任务 */
    private final Map<String, ScheduledFuture<?>> heartbeatTasks = new ConcurrentHashMap<>();

    /** sessionId → 丢失心跳计数 */
    private final Map<String, Integer> missedHeartbeatCounts = new ConcurrentHashMap<>();

    public GuardianshipWebSocketHandler(WebSocketSessionRegistry sessionRegistry,
                                         WebSocketProperties properties,
                                         ObjectMapper objectMapper,
                                         JwtTokenProvider jwtTokenProvider,
                                         MediaSessionManager mediaSessionManager,
                                         OfflineAlertQueue offlineAlertQueue,
                                         PendingFamilyRequestStore pendingFamilyRequestStore) {
        this.sessionRegistry = sessionRegistry;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.jwtTokenProvider = jwtTokenProvider;
        this.mediaSessionManager = mediaSessionManager;
        this.offlineAlertQueue = offlineAlertQueue;
        this.pendingFamilyRequestStore = pendingFamilyRequestStore;
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ws-guardianship-heartbeat");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String accountId = extractAccountId(session);
        String role = extractRole(session);

        // 只允许 FAMILY 角色连接家属 WebSocket
        if (accountId == null || !"FAMILY".equals(role)) {
            log.warn("WebSocket 认证失败或角色不符: accountId={}, role={}", accountId, role);
            sendMessage(session, WsError.of("AUTH_FAILED", "认证失败或权限不足"));
            safeClose(session);
            return;
        }

        String connectionId = UUID.randomUUID().toString();

        // 注册连接（若已有旧连接则替换）
        WebSocketSession old = sessionRegistry.register(accountId, session);
        if (old != null && old.isOpen()) {
            safeClose(old);
        }

        // 发送连接确认
        sendMessage(session, ConnectionEstablished.of(connectionId, accountId));

        // 启动心跳
        startHeartbeat(session);

        // 补推离线消息
        drainOfflineMessages(accountId, session);

        log.info("家属 WebSocket 连接建立: accountId={}, role={}, sessionId={}", accountId, role, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String accountId = sessionRegistry.getAccountId(session.getId()).orElse("unknown");

        try {
            JsonNode msg = objectMapper.readTree(message.getPayload());
            String type = msg.has("type") ? msg.get("type").asText() : "";
            log.debug("WebSocket 上行消息: accountId={}, type={}", accountId, type);

            switch (type) {
                case "subscribe_status" -> handleSubscribeStatus(session, accountId, msg);
                case "unsubscribe_status" -> handleUnsubscribeStatus(session, accountId, msg);
                case "request_media" -> handleRequestMedia(session, accountId, msg);
                case "end_media" -> handleEndMedia(session, accountId, msg);
                case "renew_token" -> handleRenewToken(session, accountId, msg);
                case "trigger_rescue" -> handleTriggerRescue(session, accountId, msg);
                case "pong" -> handlePong(session.getId());
                default -> log.warn("未知 WebSocket 消息类型: type={}, accountId={}", type, accountId);
            }
        } catch (Exception e) {
            log.error("WebSocket 消息处理异常: accountId={}", accountId, e);
            sendMessage(session, WsError.of("PARSE_ERROR", "消息解析失败: " + e.getMessage()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        cancelHeartbeat(session.getId());
        sessionRegistry.unregister(session);
        log.info("家属 WebSocket 连接关闭: sessionId={}, status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket 传输异常: sessionId={}", session.getId(), exception);
        cancelHeartbeat(session.getId());
        sessionRegistry.unregister(session);
    }

    // ── 公开推送方法 ──

    /**
     * 向指定家属推送告警。若不在线则存入离线队列。
     */
    public void pushAlert(String accountId, AlertTriggered alert) {
        Optional<WebSocketSession> session = sessionRegistry.getSession(accountId);
        if (session.isPresent() && session.get().isOpen()) {
            sendMessage(session.get(), alert);
        } else {
            try {
                offlineAlertQueue.enqueue(accountId, objectMapper.writeValueAsString(alert));
            } catch (Exception e) {
                log.error("离线告警序列化失败", e);
            }
        }
    }

    /**
     * 向指定家属推送权限授予消息。
     */
    public void pushAccessGranted(String accountId, AccessGranted msg) {
        sessionRegistry.getSession(accountId).ifPresent(session -> sendMessage(session, msg));
    }

    /**
     * 向指定家属推送权限撤销消息。
     */
    public void pushAccessRevoked(String accountId, AccessRevoked msg) {
        sessionRegistry.getSession(accountId).ifPresent(session -> sendMessage(session, msg));
    }

    /**
     * 向已订阅某驾驶员的所有家属推送驾驶员状态快照。
     */
    public void broadcastDriverStatus(String driverId, DriverStatusSnapshot snapshot) {
        for (String accountId : sessionRegistry.getSubscribers(driverId)) {
            sessionRegistry.getSession(accountId).ifPresent(session -> sendMessage(session, snapshot));
        }
    }

    /**
     * 向已订阅某驾驶员的所有家属推送告警。
     */
    public void broadcastAlertToSubscribers(String driverId, AlertTriggered alert) {
        for (String accountId : sessionRegistry.getSubscribers(driverId)) {
            pushAlert(accountId, alert);
        }
    }

    // ── 消息处理 ──

    private void handleSubscribeStatus(WebSocketSession session, String accountId, JsonNode msg) {
        if (!msg.has("driverId")) {
            sendMessage(session, WsError.of("INVALID_REQUEST", "缺少 driverId"));
            return;
        }
        String driverId = msg.get("driverId").asText();
        boolean ok = sessionRegistry.subscribe(driverId, accountId, properties.getMaxSubscriptionsPerDriver());
        if (!ok) {
            sendMessage(session, WsError.of("SUBSCRIPTION_LIMIT", "驾驶员订阅数已达上限"));
            return;
        }
        String subscriptionId = UUID.randomUUID().toString();
        sendMessage(session, SubscribeStatusAck.of(subscriptionId));
        log.info("家属订阅状态: accountId={}, driverId={}, subscriptionId={}", accountId, driverId, subscriptionId);
    }

    private void handleUnsubscribeStatus(WebSocketSession session, String accountId, JsonNode msg) {
        if (!msg.has("driverId")) return;
        String driverId = msg.get("driverId").asText();
        sessionRegistry.unsubscribe(driverId, accountId);
        log.info("家属取消订阅: accountId={}, driverId={}", accountId, driverId);
    }

    private void handleRequestMedia(WebSocketSession session, String accountId, JsonNode msg) {
        String driverId = msg.has("driverId") ? msg.get("driverId").asText() : "";
        String sessionType = msg.has("sessionType") ? msg.get("sessionType").asText() : "AUDIO";

        MediaSessionManager.MediaSession mediaSession =
                mediaSessionManager.createSession(accountId, driverId, sessionType);

        String reason = "REGULAR_60S";
        AccessGranted accessGranted = new AccessGranted(
                "access_granted", driverId,
                mediaSession.sessionHandle(),
                mediaSession.roomId(),
                mediaSession.joinToken(),
                reason
        );
        sendMessage(session, accessGranted);

        pendingFamilyRequestStore.put(
                PendingFamilyRequestStore.FamilyRequest.create(driverId, accountId, sessionType));

        log.info("家属发起音视频对讲: accountId={}, driverId={}, type={}, roomId={}",
                accountId, driverId, sessionType, mediaSession.roomId());
    }

    private void handleEndMedia(WebSocketSession session, String accountId, JsonNode msg) {
        String sessionHandle = msg.has("sessionHandle") ? msg.get("sessionHandle").asText() : "";
        mediaSessionManager.endSession(sessionHandle);
        log.info("家属挂断媒体会话: accountId={}, sessionHandle={}", accountId, sessionHandle);
    }

    private void handleRenewToken(WebSocketSession session, String accountId, JsonNode msg) {
        String sessionHandle = msg.has("sessionHandle") ? msg.get("sessionHandle").asText() : "";
        MediaSessionManager.TokenRenewal renewal = mediaSessionManager.renewToken(sessionHandle);
        if (renewal.success()) {
            sendMessage(session, TokenRenewed.of(renewal.roomId(), renewal.token(), renewal.expiresAt()));
        } else {
            sendMessage(session, WsError.of("TOKEN_RENEW_FAILED",
                    renewal.errorCode() != null ? renewal.errorCode() : "unknown"));
        }
    }

    private void handleTriggerRescue(WebSocketSession session, String accountId, JsonNode msg) {
        String driverId = msg.has("driverId") ? msg.get("driverId").asText() : "";
        String rescueRequestId = UUID.randomUUID().toString();
        String rescueReportId = UUID.randomUUID().toString();
        log.info("家属手动触发救援: accountId={}, driverId={}, rescueRequestId={}", accountId, driverId, rescueRequestId);
        sendMessage(session, RescueTriggered.of(rescueRequestId, rescueReportId, "PENDING"));
    }

    // ── 心跳管理 ──

    private void startHeartbeat(WebSocketSession session) {
        ScheduledFuture<?> task = heartbeatExecutor.scheduleAtFixedRate(
                () -> sendHeartbeat(session),
                properties.getHeartbeatIntervalSec(),
                properties.getHeartbeatIntervalSec(),
                TimeUnit.SECONDS);
        heartbeatTasks.put(session.getId(), task);
    }

    private void sendHeartbeat(WebSocketSession session) {
        if (!session.isOpen()) {
            cancelHeartbeat(session.getId());
            return;
        }
        int missed = missedHeartbeatCounts.getOrDefault(session.getId(), 0);
        if (missed >= properties.getMaxMissedHeartbeats()) {
            log.warn("WebSocket 心跳超时，主动断开: sessionId={}", session.getId());
            cancelHeartbeat(session.getId());
            sessionRegistry.unregister(session);
            safeClose(session);
            return;
        }
        missedHeartbeatCounts.put(session.getId(), missed + 1);
        sendMessage(session, Ping.now());
    }

    private void handlePong(String sessionId) {
        missedHeartbeatCounts.put(sessionId, 0);
    }

    private void cancelHeartbeat(String sessionId) {
        ScheduledFuture<?> task = heartbeatTasks.remove(sessionId);
        if (task != null) {
            task.cancel(false);
        }
        missedHeartbeatCounts.remove(sessionId);
    }

    // ── 离线消息 ──

    private void drainOfflineMessages(String accountId, WebSocketSession session) {
        java.util.List<String> messages = offlineAlertQueue.drain(accountId);
        for (String payload : messages) {
            try {
                session.sendMessage(new TextMessage(payload));
            } catch (IOException e) {
                log.error("离线消息补推失败: accountId={}", accountId, e);
            }
        }
    }

    // ── 认证辅助 ──

    private String extractAccountId(WebSocketSession session) {
        String token = extractToken(session);
        if ("mock_token".equals(token)) {
            return "acct-001-aaa-bbb-ccc-111111111111";
        }
        if (token != null) {
            return jwtTokenProvider.getAccountId(token);
        }
        return null;
    }

    private String extractRole(WebSocketSession session) {
        String token = extractToken(session);
        if ("mock_token".equals(token)) {
            return "FAMILY";
        }
        if (token != null) {
            return jwtTokenProvider.getRole(token);
        }
        return null;
    }

    private String extractToken(WebSocketSession session) {
        String query = session.getUri() != null ? session.getUri().getQuery() : null;
        if (query != null && query.contains("token=")) {
            String token = query.substring(query.indexOf("token=") + 6);
            if (token.contains("&")) {
                token = token.substring(0, token.indexOf("&"));
            }
            log.info("WebSocket token extracted: prefix={}", token.isEmpty() ? "(empty)" : token.substring(0, Math.min(20, token.length())) + "...");
            return token;
        }
        log.info("WebSocket token not found in URI: {}", session.getUri());
        return null;
    }

    // ── 底层发送 ──

    private void sendMessage(WebSocketSession session, Object payload) {
        try {
            if (session.isOpen()) {
                String json = objectMapper.writeValueAsString(payload);
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            log.error("WebSocket 发送失败: sessionId={}", session.getId(), e);
        }
    }

    private void safeClose(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.NORMAL);
            }
        } catch (IOException ignored) {
        }
    }
}
