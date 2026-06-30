package com.aiot.interfaces.rest;

import com.aiot.infra.persistence.AlertEventJpaEntity;
import com.aiot.infra.persistence.TripJpaEntity;
import com.aiot.infra.persistence.VehicleJpaEntity;
import com.aiot.infra.repository.AlertEventJpaRepository;
import com.aiot.infra.repository.TripJpaRepository;
import com.aiot.infra.repository.VehicleJpaRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/safety")
public class SafetyController {

    private final TripJpaRepository tripRepo;
    private final AlertEventJpaRepository alertRepo;
    private final VehicleJpaRepository vehicleRepo;

    public SafetyController(TripJpaRepository tripRepo, AlertEventJpaRepository alertRepo, VehicleJpaRepository vehicleRepo) {
        this.tripRepo = tripRepo;
        this.alertRepo = alertRepo;
        this.vehicleRepo = vehicleRepo;
    }

    @GetMapping("/trip/list")
    public List<TripJpaEntity> listTrips(
            @RequestParam(required = false) String driverId,
            @RequestParam(required = false) Boolean active) {
        if (active != null && active) return tripRepo.findActiveTrips();
        if (driverId != null && !driverId.isEmpty()) return tripRepo.findByDriverId(driverId);
        return tripRepo.findAll();
    }

    @GetMapping("/alert/list")
    public List<AlertEventJpaEntity> listAlerts(
            @RequestParam(required = false) String driverId,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String alertType) {
        return alertRepo.findFiltered(
            driverId == null || driverId.isEmpty() ? null : driverId,
            riskLevel == null || riskLevel.isEmpty() ? null : riskLevel,
            alertType == null || alertType.isEmpty() ? null : alertType
        );
    }

    @GetMapping("/vehicle/list")
    public List<VehicleJpaEntity> listVehicles(
            @RequestParam(required = false) String fleetId,
            @RequestParam(required = false) String keyword) {
        if (keyword != null && !keyword.isEmpty()) return vehicleRepo.findByLicensePlateLike(keyword);
        if (fleetId != null && !fleetId.isEmpty()) return vehicleRepo.findByFleetId(fleetId);
        return vehicleRepo.findAll();
    }
}
