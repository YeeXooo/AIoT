package com.aiot.domain.intervention;

import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.TripId;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.event.DomainEventPublisher;
import com.aiot.domain.event.EmergencyActivatedEvent;
import com.aiot.domain.port.NotificationPort;
import com.aiot.domain.port.NotificationPort.NotificationPayload;
import com.aiot.domain.port.NotificationPort.NotificationType;
import com.aiot.domain.port.NotificationPort.NotificationPriority;
import com.aiot.domain.port.RescueReportPort;
import com.aiot.domain.shared.AccountId;
import com.aiot.domain.model.Driver;
import com.aiot.domain.model.Trip;
import com.aiot.domain.repository.DriverRepository;
import com.aiot.domain.repository.TripRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class EmergencyResponseService {

    private final TripRepository tripRepo;
    private final DriverRepository driverRepo;
    private final NotificationPort notificationPort;
    private final RescueReportPort rescueReportPort;
    private final DomainEventPublisher eventPublisher;

    public EmergencyResponseService(TripRepository tripRepo, DriverRepository driverRepo,
                                     NotificationPort notificationPort,
                                     RescueReportPort rescueReportPort,
                                     DomainEventPublisher eventPublisher) {
        this.tripRepo = tripRepo;
        this.driverRepo = driverRepo;
        this.notificationPort = notificationPort;
        this.rescueReportPort = rescueReportPort;
        this.eventPublisher = eventPublisher;
    }

    public Result<Void, AppError> respondToEmergency(TripId tripId) {
        Optional<Trip> tripOpt = tripRepo.findById(tripId);
        if (tripOpt.isEmpty()) return Result.err(AppError.notFound("Trip not found: " + tripId));

        Trip trip = tripOpt.get();
        DriverId driverId = new DriverId(trip.getDriverId());

        NotificationPayload payload = new NotificationPayload(
            NotificationType.ALERT, "Emergency Alert",
            "Emergency detected for driver " + driverId.id(), NotificationPriority.URGENT);

        try {
            notificationPort.pushNotification(new AccountId("emergency-contact"), payload);
        } catch (Exception e) {
            return Result.err(AppError.internal("Notification failed: " + e.getMessage()));
        }

        EmergencyActivatedEvent event = new EmergencyActivatedEvent(
            tripId, Instant.now());
        eventPublisher.publish(event);
        return Result.ok(null);
    }
}
