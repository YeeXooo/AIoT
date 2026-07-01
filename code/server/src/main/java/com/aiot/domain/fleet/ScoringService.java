package com.aiot.domain.fleet;

import com.aiot.domain.event.DomainEventPublisher;
import com.aiot.domain.event.TripScoredEvent;
import com.aiot.domain.model.Trip;
import com.aiot.domain.shared.TripId;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.repository.TripRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class ScoringService {

    private final TripRepository tripRepo;
    private final DomainEventPublisher eventPublisher;

    public ScoringService(TripRepository tripRepo, DomainEventPublisher eventPublisher) {
        this.tripRepo = tripRepo;
        this.eventPublisher = eventPublisher;
    }

    public void scoreTrip(TripId tripId) {
        Optional<Trip> tripOpt = tripRepo.findById(tripId);
        if (tripOpt.isEmpty()) return;

        Trip trip = tripOpt.get();
        // 第一期打桩：急刹 -15分，急加速 -10分
        int baseScore = 100;
        int hardBraking = trip.getHardBrakingCount() != null ? trip.getHardBrakingCount() : 0;
        int hardAccel = trip.getHardAccelerationCount() != null ? trip.getHardAccelerationCount() : 0;
        int score = Math.max(0, baseScore - hardBraking * 15 - hardAccel * 10);

        TripScoredEvent event = new TripScoredEvent(
            tripId, new DriverId(trip.getDriverId()), score, Instant.now());
        eventPublisher.publish(event);
    }
}
