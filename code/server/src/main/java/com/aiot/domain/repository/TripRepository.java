package com.aiot.domain.repository;

import com.aiot.domain.model.Trip;

import java.util.List;
import java.util.Optional;

public interface TripRepository {
    void save(Trip trip);
    Optional<Trip> findById(String id);
    List<Trip> findByDriverId(String driverId);
    List<Trip> findActiveTrips();
    List<Trip> findAll();
    void delete(String id);
}
