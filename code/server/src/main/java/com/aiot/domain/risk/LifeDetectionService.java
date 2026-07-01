package com.aiot.domain.risk;

import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.TripId;
import com.aiot.domain.event.DomainEventPublisher;
import com.aiot.domain.event.LifeDetectedEvent;
import com.aiot.domain.model.Trip;
import com.aiot.domain.repository.TripRepository;
import com.aiot.domain.repository.VehicleRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class LifeDetectionService {

    private final TripRepository tripRepo;
    private final VehicleRepository vehicleRepo;
    private final DomainEventPublisher eventPublisher;

    public LifeDetectionService(TripRepository tripRepo,
                                 VehicleRepository vehicleRepo,
                                 DomainEventPublisher eventPublisher) {
        this.tripRepo = tripRepo;
        this.vehicleRepo = vehicleRepo;
        this.eventPublisher = eventPublisher;
    }

    public Result<LifeDetectedEvent, AppError> detectLife(TripId tripId, boolean radarMotionDetected) {
        Optional<Trip> tripOpt = tripRepo.findById(tripId);
        if (tripOpt.isEmpty()) return Result.err(AppError.notFound("Trip not found: " + tripId));

        Trip trip = tripOpt.get();
        if (radarMotionDetected && trip.getEndedAt() != null) {
            LifeDetectedEvent event = new LifeDetectedEvent(
                tripId, trip.getVehicleId(), Instant.now());
            eventPublisher.publish(event);
            return Result.ok(event);
        }
        return Result.ok(null);
    }
}
