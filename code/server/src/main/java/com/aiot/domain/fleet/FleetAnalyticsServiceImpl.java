package com.aiot.domain.fleet;

import com.aiot.domain.event.RiskLevel;
import com.aiot.domain.model.Driver;
import com.aiot.domain.model.Trip;
import com.aiot.domain.model.Vehicle;
import com.aiot.domain.repository.DriverRepository;
import com.aiot.domain.repository.TripRepository;
import com.aiot.domain.repository.VehicleRepository;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FleetAnalyticsServiceImpl implements FleetAnalyticsService {

    // TODO: Replace hardcoded constants with actual query logic
    private static final double L1_FATIGUE_RATE = 0.05;
    private static final double L2_FATIGUE_RATE = 0.15;
    private static final double L3_FATIGUE_RATE = 0.30;

    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final TripRepository tripRepository;
    private final ScoringService scoringService;

    public FleetAnalyticsServiceImpl(VehicleRepository vehicleRepository,
                                      DriverRepository driverRepository,
                                      TripRepository tripRepository,
                                      ScoringService scoringService) {
        this.vehicleRepository = vehicleRepository;
        this.driverRepository = driverRepository;
        this.tripRepository = tripRepository;
        this.scoringService = scoringService;
    }

    @Override
    public Result<FatigueDistribution, AppError> getFleetFatigueDistribution(String fleetId) {
        List<Vehicle> fleetVehicles = vehicleRepository.findByFleetId(fleetId);
        if (fleetVehicles.isEmpty()) {
            return Result.err(AppError.notFound("Fleet", fleetId));
        }

        // TODO: Replace placeholder data with actual driver fatigue calculation
        Map<String, Double> distribution = new HashMap<>();
        distribution.put("LOW_FATIGUE", L1_FATIGUE_RATE * 100);
        distribution.put("MODERATE_FATIGUE", L2_FATIGUE_RATE * 100);
        distribution.put("HIGH_FATIGUE", L3_FATIGUE_RATE * 100);

        return Result.ok(new FatigueDistribution(distribution));
    }

    @Override
    public Result<List<DriverSummary>, AppError> drillDown(RiskLevel riskLevel) {
        List<Driver> allDrivers = driverRepository.findAll();
        List<DriverSummary> summaries = new ArrayList<>();

        for (Driver driver : allDrivers) {
            if (matchesRiskLevel(driver, riskLevel)) {
                Result<Double, AppError> scoreResult = scoringService.calculateDriverCompositeScore(driver.driverId().id());
                double score = scoreResult.isOk() ? scoreResult.unwrap() : 0.0;
                summaries.add(new DriverSummary(
                        driver.driverId().id(),
                        score,
                        buildSummary(driver, score, riskLevel)
                ));
            }
        }

        return Result.ok(summaries);
    }

    private boolean matchesRiskLevel(Driver driver, RiskLevel riskLevel) {
        List<Trip> trips = tripRepository.findByDriverId(driver.driverId().id());
        if (trips.isEmpty()) {
            return riskLevel == RiskLevel.L1_HINT;
        }

        long totalEvents = trips.stream()
                .mapToLong(t -> t.drivingBehaviorCounters().getSuddenBrakingCount()
                        + t.drivingBehaviorCounters().getSuddenAccelerationCount())
                .sum();
        return switch (riskLevel) {
            case L1_HINT -> totalEvents < 5;
            case L2_WARNING -> totalEvents >= 5 && totalEvents < 20;
            case L3_CRITICAL -> totalEvents >= 20;
        };
    }

    private String buildSummary(Driver driver, double score, RiskLevel riskLevel) {
        return String.format("Driver %s with score %.1f at risk level %s",
                driver.name(), score, riskLevel.name());
    }
}
