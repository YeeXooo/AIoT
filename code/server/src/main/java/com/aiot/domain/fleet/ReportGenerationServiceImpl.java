package com.aiot.domain.fleet;

import com.aiot.domain.event.DomainEventPublisher;
import com.aiot.domain.event.PerformanceWarningEvent;
import com.aiot.domain.model.Driver;
import com.aiot.domain.model.TimeRange;
import com.aiot.domain.model.Trip;
import com.aiot.domain.model.Vehicle;
import com.aiot.domain.repository.DriverRepository;
import com.aiot.domain.repository.TripRepository;
import com.aiot.domain.repository.VehicleRepository;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.Result;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ReportGenerationServiceImpl implements ReportGenerationService {

    private static final double PERFORMANCE_WARNING_THRESHOLD = 60.0;

    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final TripRepository tripRepository;
    private final ScoringService scoringService;
    private final DomainEventPublisher eventPublisher;

    public ReportGenerationServiceImpl(VehicleRepository vehicleRepository,
                                        DriverRepository driverRepository,
                                        TripRepository tripRepository,
                                        ScoringService scoringService,
                                        DomainEventPublisher eventPublisher) {
        this.vehicleRepository = vehicleRepository;
        this.driverRepository = driverRepository;
        this.tripRepository = tripRepository;
        this.scoringService = scoringService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Result<FleetReport, AppError> generateFleetReport(String fleetId) {
        List<Vehicle> fleetVehicles = vehicleRepository.findByFleetId(fleetId);
        if (fleetVehicles.isEmpty()) {
            return Result.err(AppError.notFound("Fleet", fleetId));
        }

        List<Driver> allDrivers = driverRepository.findAll();
        double totalScore = 0;
        int driverCount = 0;

        for (Driver driver : allDrivers) {
            Result<Double, AppError> scoreResult = scoringService.calculateDriverCompositeScore(driver.driverId().id());
            if (scoreResult.isOk()) {
                totalScore += scoreResult.unwrap();
                driverCount++;
            }
        }

        double avgScore = driverCount > 0 ? totalScore / driverCount : 100.0;
        String summary = String.format("Fleet %s: %d vehicles, %d drivers, avg score %.1f",
                fleetId, fleetVehicles.size(), driverCount, avgScore);

        return Result.ok(new FleetReport(fleetId, fleetVehicles.size(), driverCount, avgScore, summary));
    }

    @Override
    public Result<DriverReport, AppError> generateDriverReport(DriverId driverId, TimeRange timeRange) {
        return driverRepository.findById(driverId.id())
                .flatMap(driver -> {
                    List<Trip> trips = tripRepository.findByDriverId(driverId.id());
                    Result<Double, AppError> scoreResult = scoringService.calculateDriverCompositeScore(driverId.id());
                    if (scoreResult.isErr()) {
                        return Optional.of(Result.<DriverReport, AppError>err(scoreResult.unwrapErr()));
                    }
                    double score = scoreResult.unwrap();
                    String summary = String.format("Driver %s: %d trips, composite score %.1f",
                            driver.name(), trips.size(), score);

                    if (score < PERFORMANCE_WARNING_THRESHOLD) {
                        eventPublisher.publish(new PerformanceWarningEvent(
                                driver.driverId(),
                                (int) Math.round(score),
                                "DRIVER_REPORT",
                                "Driver score below threshold",
                                Instant.now()
                        ));
                    }

                    return Optional.of(Result.<DriverReport, AppError>ok(
                            new DriverReport(driverId.id(), driver.name(), score, trips.size(), summary)));
                })
                .orElse(Result.err(AppError.notFound("Driver", driverId.id())));
    }
}
