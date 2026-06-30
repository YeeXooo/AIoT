package com.aiot.domain.repository;

import com.aiot.domain.shared.AggregateId;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository {
    VehicleRepository save(Vehicle vehicle);
    Optional<Vehicle> findById(AggregateId id);
    List<Vehicle> findByFleetId(String fleetId);
    List<Vehicle> findByLicensePlateLike(String keyword);
    List<Vehicle> findAll();
    void delete(AggregateId id);

    interface Vehicle {
        AggregateId getId();
        String getLicensePlate();
        String getVin();
        String getTerminalSn();
        String getFleetId();
        String getFirmwareVersion();
        String getSensorStatus();
    }
}
