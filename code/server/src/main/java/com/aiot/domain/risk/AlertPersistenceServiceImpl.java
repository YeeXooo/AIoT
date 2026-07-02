package com.aiot.domain.risk;

import com.aiot.domain.event.AlertType;
import com.aiot.domain.event.DomainEventPublisher;
import com.aiot.domain.event.EmergencyActivatedEvent;
import com.aiot.domain.event.LifeDetectedEvent;
import com.aiot.domain.event.RiskDeterminedEvent;
import com.aiot.domain.event.RiskLevel;
import com.aiot.domain.model.GeoLocation;
import com.aiot.domain.model.SafetyAlertEvent;
import com.aiot.domain.model.Trip;
import com.aiot.domain.repository.TripRepository;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.TripId;
import com.aiot.domain.shared.VehicleId;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

public class AlertPersistenceServiceImpl implements AlertPersistenceService {

    private final DomainEventPublisher eventPublisher;
    private final TripRepository tripRepository;
    private DomainEventPublisher.Subscription riskDeterminedSub;
    private DomainEventPublisher.Subscription lifeDetectedSub;
    private DomainEventPublisher.Subscription emergencyActivatedSub;

    public AlertPersistenceServiceImpl(DomainEventPublisher eventPublisher,
                                        TripRepository tripRepository) {
        this.eventPublisher = eventPublisher;
        this.tripRepository = tripRepository;
    }

    @Override
    public void start() {
        riskDeterminedSub = eventPublisher.registerAsyncHandler("RiskDeterminedEvent", (RiskDeterminedEvent event) -> {
            handleRiskDetermined(event);
        });

        lifeDetectedSub = eventPublisher.registerAsyncHandler("LifeDetectedEvent", (LifeDetectedEvent event) -> {
            handleLifeDetected(event);
        });

        emergencyActivatedSub = eventPublisher.registerAsyncHandler("EmergencyActivatedEvent", (EmergencyActivatedEvent event) -> {
            handleEmergencyActivated(event);
        });
    }

    @Override
    public void stop() {
        if (riskDeterminedSub != null) {
            riskDeterminedSub.cancel();
        }
        if (lifeDetectedSub != null) {
            lifeDetectedSub.cancel();
        }
        if (emergencyActivatedSub != null) {
            emergencyActivatedSub.cancel();
        }
    }

    private void handleRiskDetermined(RiskDeterminedEvent event) {
        TripId tripId = event.tripId();
        Optional<Trip> tripOpt = tripRepository.findById(tripId.id());
        if (tripOpt.isEmpty()) {
            return;
        }
        Trip trip = tripOpt.get();
        DriverId driverId = trip.driverId();
        VehicleId vehicleId = trip.vehicleId();

        SafetyAlertEvent alert = SafetyAlertEvent.create(
                tripId, driverId, vehicleId,
                event.alertType(), event.riskLevel(),
                toLocalDateTime(event.detectionTime()),
                null, null,
                event.anomalyDescription()
        );

        alert.validate();
    }

    private void handleLifeDetected(LifeDetectedEvent event) {
        VehicleId vehicleId = event.vehicleId();
        List<Trip> activeTrips = tripRepository.findActiveTrips();
        Optional<Trip> matchingTrip = activeTrips.stream()
                .filter(t -> t.vehicleId().id().equals(vehicleId.id()))
                .findFirst();
        if (matchingTrip.isEmpty()) {
            return;
        }
        Trip trip = matchingTrip.get();

        SafetyAlertEvent alert = SafetyAlertEvent.create(
                trip.tripId(), trip.driverId(), trip.vehicleId(),
                AlertType.LIFE_DETECTION,
                RiskLevel.L2_WARNING,
                toLocalDateTime(event.detectionTime()),
                null, null,
                "Life detected in vehicle: confidence=" + String.format("%.2f", event.confidence())
        );

        alert.validate();
    }

    private void handleEmergencyActivated(EmergencyActivatedEvent event) {
        DriverId driverId = event.driverId();
        List<Trip> activeTrips = tripRepository.findActiveTrips();
        Optional<Trip> matchingTrip = activeTrips.stream()
                .filter(t -> t.driverId().id().equals(driverId.id()))
                .findFirst();
        if (matchingTrip.isEmpty()) {
            return;
        }
        Trip trip = matchingTrip.get();

        GeoLocation location = new GeoLocation(event.latitude(), event.longitude());

        SafetyAlertEvent alert = SafetyAlertEvent.create(
                trip.tripId(), trip.driverId(), trip.vehicleId(),
                AlertType.COLLISION_DISABILITY,
                RiskLevel.L3_CRITICAL,
                toLocalDateTime(event.eventTime()),
                location, null,
                "Emergency activated: collision disability detected"
        );

        alert.validate();
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant != null
                ? LocalDateTime.ofInstant(instant, ZoneOffset.UTC)
                : LocalDateTime.now();
    }
}
