package com.aiot.domain.risk;

import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.TripId;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.event.DomainEventPublisher;
import com.aiot.domain.event.RiskDeterminedEvent;
import com.aiot.domain.event.AlertType;
import com.aiot.domain.event.RiskLevel;
import com.aiot.domain.port.VehicleStateBuffer;
import com.aiot.domain.port.PhysiologicalDataBuffer;
import com.aiot.domain.model.TimeRange;
import com.aiot.domain.model.VehicleStateSnapshot;
import com.aiot.domain.model.PhysiologicalSnapshot;
import com.aiot.domain.model.Driver;
import com.aiot.domain.model.Trip;
import com.aiot.domain.repository.DriverRepository;
import com.aiot.domain.repository.TripRepository;
import com.aiot.domain.repository.VehicleRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class RiskDeterminationService {

    private final TripRepository tripRepo;
    private final DriverRepository driverRepo;
    private final VehicleRepository vehicleRepo;
    private final VehicleStateBuffer vehicleBuffer;
    private final PhysiologicalDataBuffer physioBuffer;
    private final DomainEventPublisher eventPublisher;

    public RiskDeterminationService(TripRepository tripRepo, DriverRepository driverRepo,
                                     VehicleRepository vehicleRepo, VehicleStateBuffer vehicleBuffer,
                                     PhysiologicalDataBuffer physioBuffer,
                                     DomainEventPublisher eventPublisher) {
        this.tripRepo = tripRepo;
        this.driverRepo = driverRepo;
        this.vehicleRepo = vehicleRepo;
        this.vehicleBuffer = vehicleBuffer;
        this.physioBuffer = physioBuffer;
        this.eventPublisher = eventPublisher;
    }

    public Result<RiskDeterminedEvent, AppError> determineRisk(TripId tripId,
            VehicleStateSnapshot vehicleSnapshot, PhysiologicalSnapshot physioSnapshot) {
        Optional<Trip> tripOpt = tripRepo.findById(tripId);
        if (tripOpt.isEmpty()) return Result.err(AppError.notFound("Trip not found: " + tripId));

        Optional<Driver> driverOpt = driverRepo.findById(new DriverId(tripOpt.get().getDriverId()));
        if (driverOpt.isEmpty()) return Result.err(AppError.notFound("Driver not found"));

        // 第一期打桩：速度 > 120 判定 DANGER
        if (vehicleSnapshot.speed() != null && vehicleSnapshot.speed() > 120.0) {
            RiskDeterminedEvent event = new RiskDeterminedEvent(
                tripId, RiskLevel.DANGER, AlertType.FATIGUE,
                Instant.now(), "Speed exceeded 120 km/h");
            eventPublisher.publish(event);
            return Result.ok(event);
        }

        return Result.ok(null);
    }
}
