package com.aiot.infra.adapter;

import com.aiot.domain.port.MediaSessionPort;
import com.aiot.domain.shared.AccountId;
import com.aiot.domain.shared.DriverId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 音视频会话适配器（打桩实现）。
 * <p>
 * 返回 mock 房间 ID 和 Token，不实际调用 SparkRTC。
 * </p>
 * <p>
 * 设计依据：docs/ood_infrastructure.md §3.4.8
 * </p>
 */
@Component
public class MediaSessionAdapter implements MediaSessionPort {

    private static final Logger log = LoggerFactory.getLogger(MediaSessionAdapter.class);

    /**
     * 活跃会话记录（sessionId -> SessionHandle）
     */
    private final ConcurrentMap<String, SessionHandle> activeSessions = new ConcurrentHashMap<>();

    @Override
    public SessionHandle establishSession(AccountId participant, DriverId driverId,
                                          SessionType sessionType) throws MediaSessionException {
        String sessionId = UUID.randomUUID().toString();
        SessionHandle handle = new SessionHandle(sessionId);

        activeSessions.put(sessionId, handle);

        log.info("Session established: sessionId={}, participant={}, driver={}, type={}",
                sessionId, participant.id(), driverId.id(), sessionType);

        return handle;
    }

    @Override
    public void terminateSession(SessionHandle handle) throws MediaSessionException {
        SessionHandle removed = activeSessions.remove(handle.sessionId());

        if (removed == null) {
            throw new MediaSessionException.SessionNotFoundException(
                    String.format("Session not found: %s", handle.sessionId()));
        }

        log.info("Session terminated: sessionId={}", handle.sessionId());
    }

    /**
     * 获取活跃会话数量。
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }
}
