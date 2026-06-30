package com.aiot.domain.repository;

import com.aiot.domain.shared.AggregateId;

import java.util.List;
import java.util.Optional;

public interface TripRepository {
    TripRepository save(Trip trip);
    Optional<Trip> findById(AggregateId id);
    List<Trip> findByDriverId(AggregateId driverId);
    List<Trip> findActiveTrips();
    List<Trip> findAll();
    void delete(AggregateId id);

    interface Trip {
        AggregateId getId();
        AggregateId getDriverId();
        AggregateId getVehicleId();
        java.time.LocalDateTime getStartedAt();
        java.time.LocalDateTime getEndedAt();
        Integer getHardBrakingCount();
        Integer getHardAccelerationCount();
        Integer getScoreValue();
    }
}
