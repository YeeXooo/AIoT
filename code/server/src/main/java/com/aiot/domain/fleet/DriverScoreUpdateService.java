package com.aiot.domain.fleet;

import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.event.DomainEventPublisher;
import com.aiot.domain.event.DriverScoreUpdatedEvent;
import com.aiot.domain.model.Driver;
import com.aiot.domain.model.Trip;
import com.aiot.domain.repository.DriverRepository;
import com.aiot.domain.repository.TripRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class DriverScoreUpdateService {

    private final DriverRepository driverRepo;
    private final TripRepository tripRepo;
    private final DomainEventPublisher eventPublisher;

    public DriverScoreUpdateService(DriverRepository driverRepo,
                                     TripRepository tripRepo,
                                     DomainEventPublisher eventPublisher) {
        this.driverRepo = driverRepo;
        this.tripRepo = tripRepo;
        this.eventPublisher = eventPublisher;
    }

    public Result<Integer, AppError> updateComprehensiveScore(DriverId driverId) {
        Optional<Driver> driverOpt = driverRepo.findById(driverId);
        if (driverOpt.isEmpty()) return Result.err(AppError.notFound("Driver not found"));

        List<Trip> trips = tripRepo.findByDriverId(driverId);
        int total = trips.stream()
            .mapToInt(t -> t.getScoreValue() != null ? t.getScoreValue() : 0)
            .sum();
        int avgScore = trips.isEmpty() ? 100 : total / trips.size();

        Driver driver = driverOpt.get();
        driver.setComprehensiveScore(avgScore);
        driverRepo.save(driver);

        DriverScoreUpdatedEvent event = new DriverScoreUpdatedEvent(
            driverId, avgScore, Instant.now());
        eventPublisher.publish(event);

        return Result.ok(avgScore);
    }
}
