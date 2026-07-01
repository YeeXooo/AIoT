package com.aiot.domain.shared;

import com.aiot.domain.shared.TripId;
import com.aiot.domain.model.Trip;
import com.aiot.domain.repository.TripRepository;
import com.aiot.domain.port.DrivingBehaviorTrackingPort;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class DrivingBehaviorTrackingService {

    private final TripRepository tripRepo;
    private final DrivingBehaviorTrackingPort behaviorPort;

    public DrivingBehaviorTrackingService(TripRepository tripRepo,
                                           DrivingBehaviorTrackingPort behaviorPort) {
        this.tripRepo = tripRepo;
        this.behaviorPort = behaviorPort;
    }

    public void trackHardBraking(TripId tripId, double deceleration) {
        Optional<Trip> trip = tripRepo.findById(tripId);
        if (trip.isEmpty()) return;

        DrivingBehaviorTrackingPort.HardBrakingEvent event =
            new DrivingBehaviorTrackingPort.HardBrakingEvent(java.time.Instant.now(), deceleration);
        behaviorPort.onHardBrakingDetected(event);
    }
}
