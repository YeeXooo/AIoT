package com.aiot.domain.emergency;

import com.aiot.domain.event.DomainEventPublisher;
import com.aiot.domain.event.EmergencyActivatedEvent;
import com.aiot.domain.event.FamilyManualRescueRequestedEvent;
import com.aiot.domain.model.Driver;
import com.aiot.domain.model.GeoLocation;
import com.aiot.domain.model.PhysiologicalSnapshot;
import com.aiot.domain.model.RescueAuthorizationToken;
import com.aiot.domain.model.RescueReport;
import com.aiot.domain.model.TimeRange;
import com.aiot.domain.model.Trip;
import com.aiot.domain.model.Vehicle;
import com.aiot.domain.model.VehicleStateSnapshot;
import com.aiot.domain.port.RescueReportPort;
import com.aiot.domain.port.VehicleStateBuffer;
import com.aiot.domain.repository.DriverRepository;
import com.aiot.domain.repository.TripRepository;
import com.aiot.domain.repository.VehicleRepository;
import com.aiot.domain.shared.AccountId;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.RescueReportId;
import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.VehicleId;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service("domainEmergencyRescueService")
public class EmergencyRescueServiceImpl implements EmergencyRescueService {

    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final TripRepository tripRepository;
    private final DomainEventPublisher eventPublisher;
    private final RescueReportPort rescueReportPort;
    private final VehicleStateBuffer vehicleStateBuffer;
    // TODO: Move tokenStore to infrastructure layer (in-memory store)
    private final Map<String, RescueAuthorizationToken> tokenStore = new ConcurrentHashMap<>();
    // TODO: Move rescueHistory to persistent repository
    private final Map<String, List<RescueRecord>> rescueHistory = new ConcurrentHashMap<>();

    public EmergencyRescueServiceImpl(
            VehicleRepository vehicleRepository,
            DriverRepository driverRepository,
            TripRepository tripRepository,
            DomainEventPublisher eventPublisher,
            RescueReportPort rescueReportPort,
            VehicleStateBuffer vehicleStateBuffer) {
        this.vehicleRepository = vehicleRepository;
        this.driverRepository = driverRepository;
        this.tripRepository = tripRepository;
        this.eventPublisher = eventPublisher;
        this.rescueReportPort = rescueReportPort;
        this.vehicleStateBuffer = vehicleStateBuffer;
    }

    @Override
    public Result<RescueReport, AppError> createRescueReport(VehicleId vehicleId, DriverId driverId) {
        Optional<Vehicle> vehicleOpt = vehicleRepository.findById(vehicleId.id());
        if (vehicleOpt.isEmpty()) {
            return Result.err(AppError.notFound("Vehicle", vehicleId.id()));
        }
        Vehicle vehicle = vehicleOpt.get();

        Optional<Driver> driverOpt = driverRepository.findById(driverId.id());
        if (driverOpt.isEmpty()) {
            return Result.err(AppError.notFound("Driver", driverId.id()));
        }
        Driver driver = driverOpt.get();

        List<Trip> activeTrips = tripRepository.findByDriverId(driverId.id()).stream()
                .filter(t -> !t.isEnded())
                .toList();
        if (activeTrips.isEmpty()) {
            return Result.err(AppError.invalidState("No active trip for driver " + driverId.id()));
        }
        Trip activeTrip = activeTrips.get(0);

        Instant now = Instant.now();
        TimeRange window = new TimeRange(now.minus(Duration.ofSeconds(30)), now);
        List<VehicleStateSnapshot> vehicleStates;
        try {
            vehicleStates = vehicleStateBuffer.getSnapshots(activeTrip.tripId(), window);
        } catch (Exception e) {
            vehicleStates = Collections.emptyList();
        }

        double lat = 0.0;
        double lng = 0.0;
        if (vehicleStates != null && !vehicleStates.isEmpty()) {
            var latest = vehicleStates.get(vehicleStates.size() - 1);
            lat = latest.latitude();
            lng = latest.longitude();
        }
        GeoLocation location = new GeoLocation(lat, lng);

        List<PhysiologicalSnapshot> vitals = activeTrip.physiologicalSnapshots();
        PhysiologicalSnapshot latestVitals = vitals.isEmpty() ? null : vitals.get(vitals.size() - 1);

        com.aiot.domain.model.RescueReport report = new com.aiot.domain.model.RescueReport(
                driverId,
                location,
                latestVitals,
                vehicleStates != null ? vehicleStates : Collections.emptyList(),
                "Automatic rescue report for driver " + driverId.id(),
                now
        );

        try {
            rescueReportPort.deliverRescueReport(report);
        } catch (Exception e) {
            return Result.err(AppError.invalidState("Failed to deliver rescue report: " + e.getMessage()));
        }

        EmergencyActivatedEvent event = new EmergencyActivatedEvent(
                driverId,
                location.latitude(),
                location.longitude(),
                vehicleStates,
                now
        );
        eventPublisher.publish(event);

        EmergencyRescueService.RescueReport result = new EmergencyRescueService.RescueReport(
                RescueReportId.generate().id(),
                vehicleId.id(),
                driverId.id(),
                location.latitude() + "," + location.longitude(),
                "DELIVERED"
        );

        rescueHistory.computeIfAbsent(driverId.id(), k -> new ArrayList<>())
                .add(new RescueRecord(result.reportId(), driverId.id(), now.toEpochMilli(), "DELIVERED"));

        return Result.ok(result);
    }

    @Override
    public Result<RescueAuthorizationToken, AppError> issueRescueAuthorization(
            RescueReportId reportId, List<String> operations, int validitySeconds) {
        if (operations == null || operations.isEmpty()) {
            return Result.err(AppError.validationFailed("operations must not be empty"));
        }
        if (validitySeconds <= 0) {
            return Result.err(AppError.validationFailed("validitySeconds must be positive"));
        }

        String tokenId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofSeconds(validitySeconds));

        RescueAuthorizationToken token = RescueAuthorizationToken.issue(
                tokenId,
                "EmergencyRescueService",
                new VehicleId(reportId.id()),
                Set.copyOf(operations),
                now,
                expiresAt
        );
        tokenStore.put(tokenId, token);

        return Result.ok(token);
    }

    @Override
    public Result<Void, AppError> verifyAndConsumeToken(String token, String operation, VehicleId vehicleId) {
        String[] parts = token.split(":");
        String tokenId = parts.length > 0 ? parts[0] : token;

        RescueAuthorizationToken storedToken = tokenStore.get(tokenId);
        if (storedToken == null) {
            return Result.err(AppError.accessDenied("Token not found: " + tokenId));
        }

        Instant now = Instant.now();
        if (!storedToken.isValid(now)) {
            return Result.err(AppError.accessDenied("Token expired or already consumed"));
        }

        if (storedToken.getTargetVehicleId() != null
                && !storedToken.getTargetVehicleId().id().equals(vehicleId.id())) {
            return Result.err(AppError.accessDenied("Token not authorized for this vehicle"));
        }

        if (!storedToken.getAuthorizedOperations().contains(operation)) {
            return Result.err(AppError.accessDenied("Token not authorized for operation: " + operation));
        }

        RescueAuthorizationToken consumed = storedToken.consume();
        tokenStore.put(tokenId, consumed);

        return Result.ok(null);
    }

    @Override
    public Result<Void, AppError> triggerManualRescue(DriverId driverId, AccountId requesterId) {
        Optional<Driver> driverOpt = driverRepository.findById(driverId.id());
        if (driverOpt.isEmpty()) {
            return Result.err(AppError.notFound("Driver", driverId.id()));
        }

        FamilyManualRescueRequestedEvent event = new FamilyManualRescueRequestedEvent(
                driverId,
                requesterId,
                0.0,
                0.0,
                Instant.now()
        );
        eventPublisher.publish(event);

        return Result.ok(null);
    }

    @Override
    public Result<List<RescueRecord>, AppError> queryRescueHistory(DriverId driverId) {
        Optional<Driver> driverOpt = driverRepository.findById(driverId.id());
        if (driverOpt.isEmpty()) {
            return Result.err(AppError.notFound("Driver", driverId.id()));
        }

        List<RescueRecord> records = rescueHistory.getOrDefault(driverId.id(), new ArrayList<>());
        return Result.ok(new ArrayList<>(records));
    }
}
