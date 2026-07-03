package com.aiot.infra.adapter;

import com.aiot.domain.port.MediaSessionPort.MediaSessionException;
import com.aiot.domain.port.MediaSessionPort.SessionHandle;
import com.aiot.domain.port.MediaSessionPort.SessionType;
import com.aiot.domain.shared.AccountId;
import com.aiot.domain.shared.DriverId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MediaSessionAdapterTest {

    private MediaSessionAdapter adapter;
    private AccountId accountId;
    private DriverId driverId;

    @BeforeEach
    void setUp() {
        adapter = new MediaSessionAdapter();
        accountId = new AccountId("acc-1");
        driverId = new DriverId("drv-1");
    }

    @Test
    void establishSession_shouldReturnNonNullHandle() throws MediaSessionException {
        SessionHandle handle = adapter.establishSession(accountId, driverId, SessionType.VIDEO);

        assertNotNull(handle);
        assertNotNull(handle.sessionId());
        assertFalse(handle.sessionId().isEmpty());
    }

    @Test
    void establishSession_shouldIncrementActiveSessionCount() throws MediaSessionException {
        assertEquals(0, adapter.getActiveSessionCount());

        adapter.establishSession(accountId, driverId, SessionType.AUDIO);
        assertEquals(1, adapter.getActiveSessionCount());

        adapter.establishSession(accountId, driverId, SessionType.VIDEO);
        assertEquals(2, adapter.getActiveSessionCount());
    }

    @Test
    void terminateSession_shouldDecrementActiveSessionCount() throws MediaSessionException {
        SessionHandle handle = adapter.establishSession(accountId, driverId, SessionType.VIDEO);
        assertEquals(1, adapter.getActiveSessionCount());

        adapter.terminateSession(handle);
        assertEquals(0, adapter.getActiveSessionCount());
    }

    @Test
    void terminateSession_shouldThrowSessionNotFoundExceptionForUnknownHandle() {
        SessionHandle unknownHandle = new SessionHandle("unknown-session-id");

        MediaSessionException.SessionNotFoundException exception = assertThrows(
                MediaSessionException.SessionNotFoundException.class,
                () -> adapter.terminateSession(unknownHandle));
        assertTrue(exception.getMessage().contains("unknown-session-id"));
    }

    @Test
    void terminateSession_shouldThrowSessionNotFoundExceptionWhenAlreadyTerminated() throws MediaSessionException {
        SessionHandle handle = adapter.establishSession(accountId, driverId, SessionType.VIDEO);
        adapter.terminateSession(handle);

        assertThrows(MediaSessionException.SessionNotFoundException.class,
                () -> adapter.terminateSession(handle));
    }

    @Test
    void establishSession_shouldCreateUniqueSessionIds() throws MediaSessionException {
        SessionHandle handle1 = adapter.establishSession(accountId, driverId, SessionType.VIDEO);
        SessionHandle handle2 = adapter.establishSession(accountId, driverId, SessionType.VIDEO);

        assertNotEquals(handle1.sessionId(), handle2.sessionId());
    }

    @Test
    void getActiveSessionCount_shouldReturnZeroInitially() {
        assertEquals(0, adapter.getActiveSessionCount());
    }

    @Test
    void establishSession_withAudioType_shouldSucceed() throws MediaSessionException {
        SessionHandle handle = adapter.establishSession(accountId, driverId, SessionType.AUDIO);

        assertNotNull(handle);
    }
}
