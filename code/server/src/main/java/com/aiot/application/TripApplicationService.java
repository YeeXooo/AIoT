package com.aiot.application;

import com.aiot.domain.model.Trip;
import com.aiot.domain.model.Vehicle;
import com.aiot.domain.repository.TripRepository;
import com.aiot.domain.repository.VehicleRepository;

import java.util.List;

public class TripApplicationService {

    private final TripRepository tripRepository;
    private final VehicleRepository vehicleRepository;

    public TripApplicationService(TripRepository tripRepository, VehicleRepository vehicleRepository) {
        this.tripRepository = tripRepository;
        this.vehicleRepository = vehicleRepository;
    }

    public List<Trip> listTrips(String driverId, Boolean active) {
        if (active != null && active) {
            return tripRepository.findActiveTrips();
        }
        if (driverId != null && !driverId.isEmpty()) {
            return tripRepository.findByDriverId(driverId);
        }
        return tripRepository.findAll();
    }

    public List<Vehicle> listVehicles(String fleetId, String keyword) {
        if (keyword != null && !keyword.isEmpty()) {
            return vehicleRepository.findByLicensePlateLike(keyword);
        }
        if (fleetId != null && !fleetId.isEmpty()) {
            return vehicleRepository.findByFleetId(fleetId);
        }
        return vehicleRepository.findAll();
    }
}
