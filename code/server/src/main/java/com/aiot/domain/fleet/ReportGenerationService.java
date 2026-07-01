package com.aiot.domain.fleet;

import com.aiot.domain.shared.TripId;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.model.Trip;
import com.aiot.domain.model.Driver;
import com.aiot.domain.repository.TripRepository;
import com.aiot.domain.repository.DriverRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class ReportGenerationService {

    private final TripRepository tripRepo;
    private final DriverRepository driverRepo;

    public ReportGenerationService(TripRepository tripRepo, DriverRepository driverRepo) {
        this.tripRepo = tripRepo;
        this.driverRepo = driverRepo;
    }

    public Map<String, Object> generateTripReport(TripId tripId) {
        Optional<Trip> tripOpt = tripRepo.findById(tripId);
        if (tripOpt.isEmpty()) return Map.of("error", "Trip not found");

        Trip trip = tripOpt.get();
        Map<String, Object> report = new HashMap<>();
        report.put("tripId", trip.getTripId());
        report.put("startedAt", trip.getStartedAt());
        report.put("endedAt", trip.getEndedAt());
        report.put("score", trip.getScoreValue());
        report.put("hardBraking", trip.getHardBrakingCount());
        report.put("hardAcceleration", trip.getHardAccelerationCount());

        Optional<Driver> driver = driverRepo.findById(new DriverId(trip.getDriverId()));
        driver.ifPresent(d -> report.put("driverName", d.getName()));

        return report;
    }
}
