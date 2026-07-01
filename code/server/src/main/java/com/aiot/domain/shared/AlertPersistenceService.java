package com.aiot.domain.shared;

import com.aiot.domain.shared.TripId;
import com.aiot.domain.event.DomainEventPublisher;
import com.aiot.domain.event.AlertTriggeredEvent;
import com.aiot.domain.event.RiskLevel;
import com.aiot.domain.port.NotificationPort;
import com.aiot.domain.port.NotificationPort.NotificationPayload;
import com.aiot.domain.port.NotificationPort.NotificationType;
import com.aiot.domain.port.NotificationPort.NotificationPriority;
import com.aiot.domain.shared.AccountId;
import com.aiot.domain.model.AlertEvent;
import com.aiot.domain.repository.AlertEventRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AlertPersistenceService {

    private final AlertEventRepository alertRepo;
    private final NotificationPort notificationPort;
    private final DomainEventPublisher eventPublisher;

    public AlertPersistenceService(AlertEventRepository alertRepo,
                                    NotificationPort notificationPort,
                                    DomainEventPublisher eventPublisher) {
        this.alertRepo = alertRepo;
        this.notificationPort = notificationPort;
        this.eventPublisher = eventPublisher;
    }

    public void persistAlert(TripId tripId, String driverId, String vehicleId,
                              String alertType, RiskLevel riskLevel, String message) {
        AlertEvent alert = new AlertEvent();
        alert.setAlertId(java.util.UUID.randomUUID().toString());
        alert.setTripId(tripId.id());
        alert.setDriverId(driverId);
        alert.setVehicleId(vehicleId);
        alert.setAlertType(alertType);
        alert.setRiskLevel(riskLevel.name());
        alert.setAlertMsg(message);
        alert.setOccurredAt(java.time.LocalDateTime.now());
        alertRepo.save(alert);

        AlertTriggeredEvent event = new AlertTriggeredEvent(
            tripId, riskLevel, alertType, Instant.now(), message);
        eventPublisher.publish(event);
    }
}
