package com.aiot.domain.risk;

import com.aiot.domain.model.DrivingBehaviorCounters;
import com.aiot.domain.model.Trip;
import com.aiot.domain.repository.TripRepository;

import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class DrivingBehaviorTrackingServiceImpl implements DrivingBehaviorTrackingService {

    private final TripRepository tripRepository;

    public DrivingBehaviorTrackingServiceImpl(TripRepository tripRepository) {
        this.tripRepository = tripRepository;
    }

    @Override
    public void onHardBrakingDetected(String tripId, long timestamp, double magnitude) {
        Optional<Trip> tripOpt = tripRepository.findById(tripId);
        tripOpt.ifPresent(trip -> {
            DrivingBehaviorCounters current = trip.drivingBehaviorCounters();
            DrivingBehaviorCounters updated = current.incrementBraking();
            trip.updateDrivingBehaviorCounters(updated);
            tripRepository.save(trip);
        });
    }

    @Override
    public void onHardAccelerationDetected(String tripId, long timestamp, double magnitude) {
        Optional<Trip> tripOpt = tripRepository.findById(tripId);
        tripOpt.ifPresent(trip -> {
            DrivingBehaviorCounters current = trip.drivingBehaviorCounters();
            DrivingBehaviorCounters updated = current.incrementAcceleration();
            trip.updateDrivingBehaviorCounters(updated);
            tripRepository.save(trip);
        });
    }
}
