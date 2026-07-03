package com.aiot.domain.guardianship;

import com.aiot.domain.model.DriverStatusSnapshot;
import com.aiot.domain.model.StatusColor;
import com.aiot.domain.port.NotificationPort;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverStatusBroadcastServiceImplTest {

    @Mock private NotificationPort notificationPort;

    private DriverStatusBroadcastServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DriverStatusBroadcastServiceImpl(notificationPort);
    }

    @Test
    void sampleDriverStatusReturnsSnapshotWithGreenStatus() {
        DriverStatusSnapshot snapshot = service.sampleDriverStatus("d1");

        assertNotNull(snapshot);
        assertNotNull(snapshot.getDriverId());
        assertEquals("d1", snapshot.getDriverId().id());
        assertEquals(StatusColor.GREEN, snapshot.getStatusColor());
        assertNotNull(snapshot.getTimestamp());
    }

    @Test
    void broadcastStatusPushesNotificationAndReturnsOk() throws Exception {
        Result<Void, AppError> result = service.broadcastStatus("d1");

        assertTrue(result.isOk());
        verify(notificationPort).pushNotification(any(), any());
    }

    @Test
    void broadcastStatusReturnsErrorWhenNotificationFails() throws Exception {
        doThrow(new NotificationPort.NotificationException.DeliveryFailedException("delivery failed"))
                .when(notificationPort).pushNotification(any(), any());

        Result<Void, AppError> result = service.broadcastStatus("d1");

        assertTrue(result.isErr());
        assertEquals("InvalidState", result.unwrapErr().code());
        assertTrue(result.unwrapErr().message().contains("delivery failed"));
    }

    @Test
    void broadcastStatusReturnsErrorOnRecipientUnreachable() throws Exception {
        doThrow(new NotificationPort.NotificationException.RecipientUnreachableException("unreachable"))
                .when(notificationPort).pushNotification(any(), any());

        Result<Void, AppError> result = service.broadcastStatus("d1");

        assertTrue(result.isErr());
        assertTrue(result.unwrapErr().message().contains("unreachable"));
    }
}
