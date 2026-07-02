package com.aiot.interfaces.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock SparkRTC 媒体会话管理器。
 * <p>
 * 本期不连接真实 SparkRTC，以本地模拟实现房间创建、Token 签发、
 * 会话建立/终止和 Token 续签等核心流程。供家属 APP 通过 WebSocket
 * 或 REST 发起对讲/视频请求时使用。
 * </p>
 * <p>
 * 设计依据：docs/ood_interface.md §3、docs/communication_architecture.md §2.5
 * </p>
 */
@Component
public class MediaSessionManager {

    private static final Logger log = LoggerFactory.getLogger(MediaSessionManager.class);

    /** sessionHandle → 会话状态 */
    private final Map<String, MediaSession> sessions = new ConcurrentHashMap<>();

    /** 会话有效期（秒） */
    private static final long SESSION_TTL_SEC = 600; // 10 分钟

    /** Token 有效期（秒） */
    private static final long TOKEN_TTL_SEC = 1800; // 30 分钟

    /**
     * 创建媒体会话（音频/视频对讲）。
     *
     * @param accountId   家属账户 ID
     * @param driverId    驾驶员 ID
     * @param sessionType 会话类型：AUDIO / VIDEO
     * @return 会话句柄
     */
    public MediaSession createSession(String accountId, String driverId, String sessionType) {
        String sessionHandle = "session-" + UUID.randomUUID();
        String roomId = "room-" + UUID.randomUUID().toString().substring(0, 8);
        String joinToken = generateMockToken(roomId, accountId);
        String mediaToken = generateMockToken(roomId, "media");

        MediaSession session = new MediaSession(
                sessionHandle,
                accountId,
                driverId,
                sessionType,
                roomId,
                joinToken,
                mediaToken,
                Instant.now(),
                Instant.now().plusSeconds(SESSION_TTL_SEC),
                MediaSessionState.ACTIVE
        );

        sessions.put(sessionHandle, session);
        log.info("媒体会话创建: sessionHandle={}, driverId={}, roomId={}, type={}",
                sessionHandle, driverId, roomId, sessionType);
        return session;
    }

    /**
     * 结束媒体会话。
     */
    public void endSession(String sessionHandle) {
        MediaSession session = sessions.remove(sessionHandle);
        if (session != null) {
            log.info("媒体会话结束: sessionHandle={}, driverId={}", sessionHandle, session.driverId);
        }
    }

    /**
     * 续签 Token（SparkRTC Token 到期前调用）。
     */
    public TokenRenewal renewToken(String sessionHandle) {
        MediaSession session = sessions.get(sessionHandle);
        if (session == null || session.state != MediaSessionState.ACTIVE) {
            return TokenRenewal.failed("SESSION_NOT_FOUND_OR_INACTIVE");
        }

        String newToken = generateMockToken(session.roomId, session.accountId);
        Instant newExpiry = Instant.now().plusSeconds(TOKEN_TTL_SEC);
        log.info("Token 续签: sessionHandle={}, roomId={}", sessionHandle, session.roomId);
        return TokenRenewal.success(session.roomId, newToken, newExpiry);
    }

    /**
     * 获取会话信息。
     */
    public MediaSession getSession(String sessionHandle) {
        MediaSession session = sessions.get(sessionHandle);
        if (session != null && session.expiresAt.isBefore(Instant.now())) {
            sessions.remove(sessionHandle);
            return null;
        }
        return session;
    }

    /**
     * 清理过期会话。
     */
    public int cleanupExpiredSessions() {
        int before = sessions.size();
        sessions.entrySet().removeIf(e -> e.getValue().expiresAt.isBefore(Instant.now()));
        int removed = before - sessions.size();
        if (removed > 0) {
            log.info("清理过期媒体会话: {} 个", removed);
        }
        return removed;
    }

    /**
     * 获取活跃会话数。
     */
    public int activeSessionCount() {
        return sessions.size();
    }

    private String generateMockToken(String roomId, String userId) {
        return "rtc-token-" + roomId + "-" + userId + "-" +
                UUID.randomUUID().toString().substring(0, 12) +
                "-" + System.currentTimeMillis() % 100000;
    }

    // ── 内部类型 ──

    public record MediaSession(
            String sessionHandle,
            String accountId,
            String driverId,
            String sessionType,
            String roomId,
            String joinToken,
            String mediaToken,
            Instant createdAt,
            Instant expiresAt,
            MediaSessionState state
    ) {}

    public enum MediaSessionState {
        ACTIVE,
        ENDED,
        EXPIRED
    }

    public record TokenRenewal(
            boolean success,
            String roomId,
            String token,
            Instant expiresAt,
            String errorCode
    ) {
        public static TokenRenewal success(String roomId, String token, Instant expiresAt) {
            return new TokenRenewal(true, roomId, token, expiresAt, null);
        }

        public static TokenRenewal failed(String errorCode) {
            return new TokenRenewal(false, null, null, null, errorCode);
        }
    }
}
