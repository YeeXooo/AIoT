package com.aiot.domain.shared;

import com.aiot.domain.shared.DriverId;
import com.aiot.domain.model.Driver;
import com.aiot.domain.repository.DriverRepository;
import com.aiot.domain.port.NotificationPort;
import com.aiot.domain.port.NotificationPort.NotificationPayload;
import com.aiot.domain.port.NotificationPort.NotificationType;
import com.aiot.domain.port.NotificationPort.NotificationPriority;
import com.aiot.domain.shared.AccountId;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class DriverStatusBroadcastService {

    private final DriverRepository driverRepo;
    private final NotificationPort notificationPort;

    public DriverStatusBroadcastService(DriverRepository driverRepo,
                                         NotificationPort notificationPort) {
        this.driverRepo = driverRepo;
        this.notificationPort = notificationPort;
    }

    public void broadcastStatus(DriverId driverId) {
        Optional<Driver> driver = driverRepo.findById(driverId);
        if (driver.isEmpty()) return;

        NotificationPayload payload = new NotificationPayload(
            NotificationType.STATUS_SNAPSHOT, "Driver Status",
            "Driver " + driverId.id() + " score: " + driver.get().getComprehensiveScore(),
            NotificationPriority.LOW);

        try {
            notificationPort.pushNotification(new AccountId("broadcast"), payload);
        } catch (Exception ignored) {
            // non-critical
        }
    }
}
