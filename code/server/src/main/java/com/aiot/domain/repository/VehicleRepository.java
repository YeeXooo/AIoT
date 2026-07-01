package com.aiot.domain.repository;

import com.aiot.domain.model.Vehicle;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository {
    void save(Vehicle vehicle);
    Optional<Vehicle> findById(String id);
    List<Vehicle> findByFleetId(String fleetId);
    List<Vehicle> findByLicensePlateLike(String keyword);
    List<Vehicle> findAll();
    void delete(String id);
}
