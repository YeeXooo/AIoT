package com.aiot.domain.guardianship;

import com.aiot.domain.model.DriverStatusSnapshot;
import com.aiot.domain.model.StatusColor;
import com.aiot.domain.port.NotificationPort;
import com.aiot.domain.port.NotificationPort.NotificationException;
import com.aiot.domain.port.NotificationPort.NotificationPayload;
import com.aiot.domain.port.NotificationPort.NotificationPriority;
import com.aiot.domain.port.NotificationPort.NotificationType;
import com.aiot.domain.shared.AccountId;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.Result;

import java.time.Instant;

public class DriverStatusBroadcastServiceImpl implements DriverStatusBroadcastService {

    private final NotificationPort notificationPort;

    public DriverStatusBroadcastServiceImpl(NotificationPort notificationPort) {
        this.notificationPort = notificationPort;
    }

    @Override
    public DriverStatusSnapshot sampleDriverStatus(String driverId) {
        return DriverStatusSnapshot.of(
                new DriverId(driverId),
                StatusColor.GREEN,
                Instant.now()
        );
    }

    @Override
    public Result<Void, AppError> broadcastStatus(String driverId) {
        DriverStatusSnapshot snapshot = sampleDriverStatus(driverId);
        NotificationPayload payload = new NotificationPayload(
                NotificationType.STATUS_SNAPSHOT,
                "Driver Status Update",
                "Driver " + driverId + " status: " + snapshot.getStatusColor(),
                resolvePriority(snapshot.getStatusColor())
        );
        try {
            notificationPort.pushNotification(new AccountId("BROADCAST"), payload);
            return Result.ok(null);
        } catch (NotificationException e) {
            return Result.err(AppError.invalidState("Broadcast failed: " + e.getMessage()));
        }
    }

    private NotificationPriority resolvePriority(StatusColor color) {
        return switch (color) {
            case GREEN -> NotificationPriority.LOW;
            case YELLOW -> NotificationPriority.NORMAL;
            case RED -> NotificationPriority.HIGH;
        };
    }
}
