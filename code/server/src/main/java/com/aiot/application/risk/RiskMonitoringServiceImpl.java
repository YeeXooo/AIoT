package com.aiot.application.risk;

import com.aiot.domain.event.AlertType;
import com.aiot.domain.event.RiskDeterminedEvent;
import com.aiot.domain.event.RiskLevel;
import com.aiot.domain.model.DetectionWindow;
import com.aiot.domain.model.DriverStatusSnapshot;
import com.aiot.domain.model.SafetyAlertEvent;
import com.aiot.domain.model.SensorReading;
import com.aiot.domain.model.StatusColor;
import com.aiot.domain.model.Trip;
import com.aiot.domain.repository.DriverRepository;
import com.aiot.domain.repository.TripRepository;
import com.aiot.domain.risk.ActiveRiskSet;
import com.aiot.domain.risk.LifeDetectionService;
import com.aiot.domain.risk.RiskDeterminationService;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.TripId;
import com.aiot.domain.shared.VehicleId;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RiskMonitoringServiceImpl implements IRiskMonitoringService {

    private final RiskDeterminationService riskDeterminationService;
    private final LifeDetectionService lifeDetectionService;
    private final TripRepository tripRepository;
    private final DriverRepository driverRepository;

    private final Map<String, RiskMonitoringSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, SafetyAlertEvent> alertStore = new ConcurrentHashMap<>();

    public RiskMonitoringServiceImpl(
            RiskDeterminationService riskDeterminationService,
            LifeDetectionService lifeDetectionService,
            TripRepository tripRepository,
            DriverRepository driverRepository) {
        this.riskDeterminationService = riskDeterminationService;
        this.lifeDetectionService = lifeDetectionService;
        this.tripRepository = tripRepository;
        this.driverRepository = driverRepository;
    }

    @Override
    public Result<StartMonitoringResponse, AppError> startMonitoringSession(DriverId driverId, VehicleId vehicleId) {
        var driverOpt = driverRepository.findById(driverId.id());
        if (driverOpt.isEmpty()) {
            return Result.err(AppError.notFound("Driver", driverId.id()));
        }

        var driver = driverOpt.get();
        if (!driver.isActive()) {
            return Result.err(AppError.invalidState("Driver " + driverId.id() + " is not active"));
        }

        List<Trip> activeTrips = tripRepository.findActiveTrips();
        Optional<Trip> driverTrip = activeTrips.stream()
                .filter(t -> t.driverId().id().equals(driverId.id())
                        && t.vehicleId().id().equals(vehicleId.id()))
                .findFirst();

        String tripId;
        ActiveRiskSet riskSet;
        DetectionWindow detectionWindow;

        if (driverTrip.isPresent()) {
            tripId = driverTrip.get().tripId().id();
            riskSet = driverTrip.get().l3DurationTracker()
                    .map(tracker -> ActiveRiskSet.empty(tripId))
                    .orElseGet(() -> ActiveRiskSet.empty(tripId));
            detectionWindow = DetectionWindow.create(Duration.ofMinutes(5), Instant.now(), Duration.ofSeconds(30));
        } else {
            tripId = null;
            riskSet = ActiveRiskSet.empty("standalone-" + driverId.id());
            detectionWindow = DetectionWindow.create(Duration.ofMinutes(5), Instant.now(), Duration.ofSeconds(30));
        }

        String sessionHandle = UUID.randomUUID().toString();
        RiskMonitoringSession session = new RiskMonitoringSession(
                sessionHandle, driverId.id(), vehicleId.id(), tripId,
                riskSet, detectionWindow, LocalDateTime.now());
        sessions.put(sessionHandle, session);

        return Result.ok(new StartMonitoringResponse(
                sessionHandle, driverId.id(), vehicleId.id(), "ACTIVE"));
    }

    @Override
    public Result<Result.Unit, AppError> processSensorReading(String sessionHandle, SensorReading reading) {
        RiskMonitoringSession session = sessions.get(sessionHandle);
        if (session == null) {
            return Result.err(AppError.notFound("RiskMonitoringSession", sessionHandle));
        }

        List<SensorReading> readings = List.of(reading);
        var result = riskDeterminationService.executeStreamFusion(readings, session.activeRiskSet);
        if (result.isErr()) {
            return Result.err(result.unwrapErr());
        }

        var determination = result.unwrap();
        ActiveRiskSet updatedRiskSet = determination.updatedRiskSet();
        session.activeRiskSet = updatedRiskSet;

        for (RiskDeterminedEvent event : determination.determinedEvents()) {
            SafetyAlertEvent alert = SafetyAlertEvent.create(
                    new TripId(session.tripId != null ? session.tripId : ""),
                    new DriverId(session.driverId),
                    new VehicleId(session.vehicleId),
                    event.alertType(),
                    event.riskLevel(),
                    LocalDateTime.now(),
                    null,
                    null,
                    event.anomalyDescription());
            alertStore.put(alert.alertId().id(), alert);
        }

        if (reading.sensorType() == SensorReading.SensorType.MILLIMETER_WAVE_RADAR) {
            var lifeResult = lifeDetectionService.evaluateLifeDetection(reading, session.detectionWindow, new VehicleId(session.vehicleId));
            if (lifeResult.isOk()) {
                var detection = lifeResult.unwrap();
                session.detectionWindow = detection.updatedWindow();
                if (detection.detectedEvent() != null) {
                    SafetyAlertEvent alert = SafetyAlertEvent.create(
                            new TripId(session.tripId != null ? session.tripId : ""),
                            new DriverId(session.driverId),
                            new VehicleId(session.vehicleId),
                            AlertType.LIFE_DETECTION,
                            RiskLevel.L3_CRITICAL,
                            LocalDateTime.now(),
                            null,
                            null,
                            "Life detection alert for driver " + session.driverId);
                    alertStore.put(alert.alertId().id(), alert);
                }
            }
        }

        session.lastUpdated = LocalDateTime.now();
        return Result.ok();
    }

    @Override
    public Result<GetDriverRiskStatusResponse, AppError> getDriverRiskStatus(DriverId driverId) {
        var driverOpt = driverRepository.findById(driverId.id());
        if (driverOpt.isEmpty()) {
            return Result.err(AppError.notFound("Driver", driverId.id()));
        }

        List<RiskMonitoringSession> driverSessions = sessions.values().stream()
                .filter(s -> s.driverId.equals(driverId.id()))
                .toList();

        List<ActiveRiskInfo> activeRisks = new ArrayList<>();
        final StatusColor[] worstColor = {StatusColor.GREEN};

        for (RiskMonitoringSession s : driverSessions) {
            for (var entry : s.activeRiskSet.activeRisks().entrySet()) {
                activeRisks.add(new ActiveRiskInfo(
                        entry.getKey().name(),
                        entry.getValue().level().name(),
                        entry.getValue().summary(),
                        LocalDateTime.ofInstant(entry.getValue().detectedAt(), java.time.ZoneId.systemDefault())));
                worstColor[0] = worseColor(worstColor[0], toStatusColor(entry.getValue().level()));
            }
        }

        activeRisks.sort(Comparator.comparing(ActiveRiskInfo::detectedAt).reversed());

        DriverStatusSnapshot latestSnapshot = driverSessions.stream()
                .max(Comparator.comparing(s -> s.lastUpdated))
                .map(s -> DriverStatusSnapshot.of(driverId, worstColor[0], Instant.now()))
                .orElseGet(() -> DriverStatusSnapshot.of(driverId, StatusColor.GREEN, Instant.now()));

        return Result.ok(new GetDriverRiskStatusResponse(
                driverId.id(),
                latestSnapshot.getStatusColor().name(),
                activeRisks,
                latestSnapshot.getTimestamp() != null
                        ? LocalDateTime.ofInstant(latestSnapshot.getTimestamp(), java.time.ZoneId.systemDefault())
                        : LocalDateTime.now()));
    }

    @Override
    public Result<QueryAlertHistoryResponse, AppError> queryAlertHistory(
            DriverId driverId, LocalDateTime from, LocalDateTime to,
            String alertType, String riskLevel, int page, int size) {

        List<SafetyAlertEvent> alerts = alertStore.values().stream()
                .filter(a -> driverId.id().equals(a.driverId().id()))
                .toList();

        List<SafetyAlertEvent> filtered = alerts.stream()
                .filter(a -> {
                    if (a.occurredAt() == null) {
                        return true;
                    }
                    boolean afterFrom = from == null || !a.occurredAt().isBefore(from);
                    boolean beforeTo = to == null || !a.occurredAt().isAfter(to);
                    return afterFrom && beforeTo;
                })
                .filter(a -> alertType == null || alertType.isBlank() || alertType.equals(a.alertType().name()))
                .filter(a -> riskLevel == null || riskLevel.isBlank() || riskLevel.equals(a.riskLevel().name()))
                .sorted(Comparator.comparing(SafetyAlertEvent::occurredAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        int totalCount = filtered.size();
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, totalCount);

        List<AlertHistoryItem> items;
        if (fromIndex < totalCount) {
            items = filtered.subList(fromIndex, toIndex).stream()
                    .map(a -> new AlertHistoryItem(
                            a.alertId().id(), a.alertType().name(), a.riskLevel().name(),
                            a.tripId().id(), a.driverId().id(), a.vehicleId().id(),
                            a.occurredAt(), a.alertMessage()))
                    .toList();
        } else {
            items = List.of();
        }

        return Result.ok(new QueryAlertHistoryResponse(items, totalCount, page, size));
    }

    private StatusColor toStatusColor(RiskLevel level) {
        return switch (level) {
            case L1_HINT -> StatusColor.GREEN;
            case L2_WARNING -> StatusColor.YELLOW;
            case L3_CRITICAL -> StatusColor.RED;
        };
    }

    private StatusColor worseColor(StatusColor a, StatusColor b) {
        if (a == StatusColor.RED || b == StatusColor.RED) return StatusColor.RED;
        if (a == StatusColor.YELLOW || b == StatusColor.YELLOW) return StatusColor.YELLOW;
        return StatusColor.GREEN;
    }

    private static class RiskMonitoringSession {
        final String sessionHandle;
        final String driverId;
        final String vehicleId;
        final String tripId;
        private ActiveRiskSet activeRiskSet;
        private DetectionWindow detectionWindow;
        LocalDateTime lastUpdated;

        RiskMonitoringSession(String sessionHandle, String driverId, String vehicleId,
                              String tripId, ActiveRiskSet activeRiskSet,
                              DetectionWindow detectionWindow, LocalDateTime lastUpdated) {
            this.sessionHandle = sessionHandle;
            this.driverId = driverId;
            this.vehicleId = vehicleId;
            this.tripId = tripId;
            this.activeRiskSet = activeRiskSet;
            this.detectionWindow = detectionWindow;
            this.lastUpdated = lastUpdated;
        }
    }
}
