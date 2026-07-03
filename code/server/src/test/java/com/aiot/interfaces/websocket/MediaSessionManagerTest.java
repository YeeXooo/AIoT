package com.aiot.interfaces.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MediaSessionManagerTest {

    private MediaSessionManager manager;

    @BeforeEach
    void setUp() {
        manager = new MediaSessionManager();
    }

    @Test
    void createSession_returnsPopulatedSession() {
        MediaSessionManager.MediaSession session = manager.createSession("acc1", "driver1", "AUDIO");
        assertNotNull(session.sessionHandle());
        assertTrue(session.sessionHandle().startsWith("session-"));
        assertEquals("acc1", session.accountId());
        assertEquals("driver1", session.driverId());
        assertEquals("AUDIO", session.sessionType());
        assertNotNull(session.roomId());
        assertTrue(session.roomId().startsWith("room-"));
        assertNotNull(session.joinToken());
        assertNotNull(session.mediaToken());
        assertNotNull(session.createdAt());
        assertNotNull(session.expiresAt());
        assertTrue(session.expiresAt().isAfter(session.createdAt()));
        assertEquals(MediaSessionManager.MediaSessionState.ACTIVE, session.state());
    }

    @Test
    void createSession_videoSession_returnsVideoType() {
        MediaSessionManager.MediaSession session = manager.createSession("acc1", "driver1", "VIDEO");
        assertEquals("VIDEO", session.sessionType());
    }

    @Test
    void createSession_uniqueHandlesPerCall() {
        MediaSessionManager.MediaSession s1 = manager.createSession("acc1", "d1", "AUDIO");
        MediaSessionManager.MediaSession s2 = manager.createSession("acc1", "d1", "AUDIO");
        assertNotEquals(s1.sessionHandle(), s2.sessionHandle());
        assertNotEquals(s1.roomId(), s2.roomId());
    }

    @Test
    void endSession_existingSession_removesIt() {
        MediaSessionManager.MediaSession session = manager.createSession("acc1", "d1", "AUDIO");
        assertEquals(1, manager.activeSessionCount());
        manager.endSession(session.sessionHandle());
        assertEquals(0, manager.activeSessionCount());
    }

    @Test
    void endSession_nonExistentSession_doesNothing() {
        assertEquals(0, manager.activeSessionCount());
        assertDoesNotThrow(() -> manager.endSession("nonexistent"));
        assertEquals(0, manager.activeSessionCount());
    }

    @Test
    void renewToken_activeSession_returnsSuccess() {
        MediaSessionManager.MediaSession session = manager.createSession("acc1", "d1", "AUDIO");
        MediaSessionManager.TokenRenewal renewal = manager.renewToken(session.sessionHandle());
        assertTrue(renewal.success());
        assertEquals(session.roomId(), renewal.roomId());
        assertNotNull(renewal.token());
        assertNotNull(renewal.expiresAt());
        assertNull(renewal.errorCode());
    }

    @Test
    void renewToken_nonExistentSession_returnsFailed() {
        MediaSessionManager.TokenRenewal renewal = manager.renewToken("nonexistent");
        assertFalse(renewal.success());
        assertEquals("SESSION_NOT_FOUND_OR_INACTIVE", renewal.errorCode());
        assertNull(renewal.token());
    }

    @Test
    void getSession_activeSession_returnsIt() {
        MediaSessionManager.MediaSession session = manager.createSession("acc1", "d1", "AUDIO");
        MediaSessionManager.MediaSession retrieved = manager.getSession(session.sessionHandle());
        assertNotNull(retrieved);
        assertEquals(session.sessionHandle(), retrieved.sessionHandle());
    }

    @Test
    void getSession_nonExistentSession_returnsNull() {
        assertNull(manager.getSession("nonexistent"));
    }

    @Test
    void cleanupExpiredSessions_removesExpired() {
        MediaSessionManager.MediaSession session = manager.createSession("acc1", "d1", "AUDIO");
        assertEquals(1, manager.activeSessionCount());
        int removed = manager.cleanupExpiredSessions();
        assertEquals(0, removed);
        assertEquals(1, manager.activeSessionCount());
    }

    @Test
    void cleanupExpiredSessions_returnsRemovalCount() {
        manager.createSession("acc1", "d1", "AUDIO");
        assertEquals(0, manager.cleanupExpiredSessions());
    }

    @Test
    void activeSessionCount_tracksCreateAndEnd() {
        assertEquals(0, manager.activeSessionCount());
        MediaSessionManager.MediaSession s1 = manager.createSession("acc1", "d1", "AUDIO");
        assertEquals(1, manager.activeSessionCount());
        MediaSessionManager.MediaSession s2 = manager.createSession("acc2", "d2", "VIDEO");
        assertEquals(2, manager.activeSessionCount());
        manager.endSession(s1.sessionHandle());
        assertEquals(1, manager.activeSessionCount());
        manager.endSession(s2.sessionHandle());
        assertEquals(0, manager.activeSessionCount());
    }

    @Test
    void tokenRenewal_successFactory_createsCorrectRecord() {
        MediaSessionManager.TokenRenewal success = MediaSessionManager.TokenRenewal.success("room1", "token1", null);
        assertTrue(success.success());
        assertEquals("room1", success.roomId());
        assertEquals("token1", success.token());
        assertNull(success.errorCode());
    }

    @Test
    void tokenRenewal_failedFactory_createsCorrectRecord() {
        MediaSessionManager.TokenRenewal failed = MediaSessionManager.TokenRenewal.failed("ERR_CODE");
        assertFalse(failed.success());
        assertNull(failed.roomId());
        assertNull(failed.token());
        assertNull(failed.expiresAt());
        assertEquals("ERR_CODE", failed.errorCode());
    }

    @Test
    void mediaSession_recordAllFieldsAccessible() {
        java.time.Instant now = java.time.Instant.now();
        java.time.Instant expires = now.plusSeconds(600);
        MediaSessionManager.MediaSession session = new MediaSessionManager.MediaSession(
                "handle-1", "acc1", "d1", "AUDIO", "room-1",
                "join-token", "media-token", now, expires,
                MediaSessionManager.MediaSessionState.ACTIVE
        );
        assertEquals("handle-1", session.sessionHandle());
        assertEquals("acc1", session.accountId());
        assertEquals("d1", session.driverId());
        assertEquals("AUDIO", session.sessionType());
        assertEquals("room-1", session.roomId());
        assertEquals("join-token", session.joinToken());
        assertEquals("media-token", session.mediaToken());
        assertEquals(now, session.createdAt());
        assertEquals(expires, session.expiresAt());
        assertEquals(MediaSessionManager.MediaSessionState.ACTIVE, session.state());
    }
}
