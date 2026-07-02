package com.aiot.application.fleet;

import com.aiot.domain.event.RiskLevel;
import com.aiot.domain.fleet.FleetAnalyticsService;
import com.aiot.domain.fleet.ReportGenerationService;
import com.aiot.domain.model.Driver;
import com.aiot.domain.model.TimeRange;
import com.aiot.domain.model.Vehicle;
import com.aiot.domain.port.NotificationPort;
import com.aiot.domain.repository.DriverRepository;
import com.aiot.domain.repository.TripRepository;
import com.aiot.domain.repository.VehicleRepository;
import com.aiot.domain.shared.AccountId;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.VehicleId;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class FleetManagementServiceImpl implements IFleetManagementService {

    private final FleetAnalyticsService fleetAnalyticsService;
    private final ReportGenerationService reportGenerationService;
    private final NotificationPort notificationPort;
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final TripRepository tripRepository;
    private final Map<String, LocalDateTime> fleetFreshness = new ConcurrentHashMap<>();
    private final Map<String, String> driverFleetIndex = new ConcurrentHashMap<>();
    private final Map<String, List<InMemoryTrajectoryPoint>> trajectoryStore = new ConcurrentHashMap<>();

    public FleetManagementServiceImpl(
            FleetAnalyticsService fleetAnalyticsService,
            ReportGenerationService reportGenerationService,
            NotificationPort notificationPort,
            VehicleRepository vehicleRepository,
            DriverRepository driverRepository,
            TripRepository tripRepository) {
        this.fleetAnalyticsService = fleetAnalyticsService;
        this.reportGenerationService = reportGenerationService;
        this.notificationPort = notificationPort;
        this.vehicleRepository = vehicleRepository;
        this.driverRepository = driverRepository;
        this.tripRepository = tripRepository;
    }

    public void updateFleetFreshness(String fleetId, LocalDateTime updatedAt) {
        fleetFreshness.put(fleetId, updatedAt);
    }

    public void registerDriverFleet(String driverId, String fleetId) {
        driverFleetIndex.put(driverId, fleetId);
    }

    public void storeTrajectoryPoint(String vehicleId, InMemoryTrajectoryPoint point) {
        trajectoryStore.computeIfAbsent(vehicleId, k -> new ArrayList<>()).add(point);
    }

    public record InMemoryTrajectoryPoint(long timestamp, double gpsLatitude, double gpsLongitude, double speed) {}

    @Override
    public Result<GetFatigueDistributionResponse, AppError> getFatigueDistribution(String fleetId) {
        Result<FleetAnalyticsService.FatigueDistribution, AppError> result =
                fleetAnalyticsService.getFleetFatigueDistribution(fleetId);
        if (result.isErr()) {
            return Result.err(result.unwrapErr());
        }
        FleetAnalyticsService.FatigueDistribution domainResult = result.unwrap();
        String freshness = fleetFreshness.getOrDefault(fleetId, LocalDateTime.now()).toString();
        return Result.ok(new GetFatigueDistributionResponse(
                domainResult.distribution(), freshness));
    }

    @Override
    public Result<GetOfflineVehiclesResponse, AppError> getOfflineVehicles(String fleetId) {
        List<Vehicle> fleetVehicles = vehicleRepository.findByFleetId(fleetId);
        if (fleetVehicles.isEmpty()) {
            return Result.ok(new GetOfflineVehiclesResponse(Collections.emptyList()));
        }
        List<OfflineVehicleSummary> offlineVehicles = new ArrayList<>();
        for (Vehicle v : fleetVehicles) {
            if (v.isMonitoringOffline()) {
                long offlineSince = v.updatedAt() != null
                        ? v.updatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        : System.currentTimeMillis();
                offlineVehicles.add(new OfflineVehicleSummary(
                        v.vehicleId().id(),
                        v.licensePlate(),
                        "",
                        "Monitoring channel disconnected",
                        offlineSince));
            }
        }
        return Result.ok(new GetOfflineVehiclesResponse(offlineVehicles));
    }

    @Override
    public Result<DrillDownResponse, AppError> drillDownHighRisk(
            String fleetId, String riskLevel, int page, int size) {
        RiskLevel level;
        try {
            level = RiskLevel.valueOf(riskLevel);
        } catch (IllegalArgumentException e) {
            return Result.err(AppError.validationFailed("Invalid risk level: " + riskLevel));
        }

        Result<List<FleetAnalyticsService.DriverSummary>, AppError> drillResult =
                fleetAnalyticsService.drillDown(level);
        if (drillResult.isErr()) {
            return Result.err(drillResult.unwrapErr());
        }

        List<FleetAnalyticsService.DriverSummary> summaries = drillResult.unwrap();

        List<FleetAnalyticsService.DriverSummary> fleetFiltered = summaries.stream()
                .filter(s -> fleetId.equals(driverFleetIndex.get(s.driverId())))
                .collect(Collectors.toList());

        long totalCount = fleetFiltered.size();
        int fromIndex = page * size;
        if (fromIndex >= fleetFiltered.size()) {
            return Result.ok(new DrillDownResponse(Collections.emptyList(), totalCount));
        }
        int toIndex = Math.min(fromIndex + size, fleetFiltered.size());
        List<FleetAnalyticsService.DriverSummary> paged = fleetFiltered.subList(fromIndex, toIndex);

        List<HighRiskDriverSummary> drivers = new ArrayList<>();
        for (FleetAnalyticsService.DriverSummary s : paged) {
            String driverName = driverRepository.findById(s.driverId())
                    .map(Driver::name)
                    .orElse("Unknown");
            drivers.add(new HighRiskDriverSummary(
                    s.driverId(),
                    driverName,
                    s.score(),
                    s.summary(),
                    Collections.emptyList()));
        }

        return Result.ok(new DrillDownResponse(drivers, totalCount));
    }

    @Override
    public Result<GenerateReportResponse, AppError> generateReport(
            DriverId driverId, TimeRange timeRange, String reportType) {
        String reportId = UUID.randomUUID().toString();

        if ("DRIVER".equalsIgnoreCase(reportType)) {
            Result<ReportGenerationService.DriverReport, AppError> result =
                    reportGenerationService.generateDriverReport(driverId, timeRange);
            if (result.isErr()) {
                return Result.err(result.unwrapErr());
            }
            ReportGenerationService.DriverReport report = result.unwrap();
            boolean isEmpty = report.tripCount() == 0;
            return Result.ok(new GenerateReportResponse(
                    reportId, report, null, isEmpty));
        }

        if ("FLEET".equalsIgnoreCase(reportType)) {
            String fleetId = tripRepository.findByDriverId(driverId.id()).stream()
                    .findFirst()
                    .flatMap(t -> vehicleRepository.findById(t.vehicleId().id()))
                    .flatMap(v -> v.fleetId())
                    .orElse("");
            Result<ReportGenerationService.FleetReport, AppError> result =
                    reportGenerationService.generateFleetReport(fleetId);
            if (result.isErr()) {
                return Result.err(result.unwrapErr());
            }
            ReportGenerationService.FleetReport report = result.unwrap();
            boolean isEmpty = report.vehicleCount() == 0;
            return Result.ok(new GenerateReportResponse(
                    reportId, report, null, isEmpty));
        }

        return Result.err(AppError.validationFailed("Unsupported report type: " + reportType));
    }

    @Override
    public Result<SubscribeResponse, AppError> subscribePerformanceWarning(
            AccountId adminId, String fleetId) {
        String subscriptionId = UUID.randomUUID().toString();
        NotificationPort.NotificationPayload payload = new NotificationPort.NotificationPayload(
                NotificationPort.NotificationType.PERFORMANCE,
                "Performance Warning Subscription",
                "Subscribed to performance warnings for fleet " + fleetId,
                NotificationPort.NotificationPriority.NORMAL);
        try {
            notificationPort.pushNotification(adminId, payload);
        } catch (NotificationPort.NotificationException e) {
            return Result.err(AppError.iotdaChannelFailure(
                    "Failed to subscribe: " + e.getMessage()));
        }
        return Result.ok(new SubscribeResponse(subscriptionId));
    }

    @Override
    public Result<TrajectoryResponse, AppError> queryVehicleTrajectory(
            VehicleId vehicleId, TimeRange timeRange, int page, int size) {
        List<InMemoryTrajectoryPoint> allPoints =
                trajectoryStore.getOrDefault(vehicleId.id(), Collections.emptyList());

        long totalCount = allPoints.size();
        int fromIndex = page * size;
        if (fromIndex >= allPoints.size()) {
            return Result.ok(new TrajectoryResponse(Collections.emptyList(), totalCount));
        }
        int toIndex = Math.min(fromIndex + size, allPoints.size());
        List<InMemoryTrajectoryPoint> paged = allPoints.subList(fromIndex, toIndex);

        List<TrajectoryPoint> points = new ArrayList<>();
        for (InMemoryTrajectoryPoint e : paged) {
            points.add(new TrajectoryPoint(
                    e.timestamp(),
                    e.gpsLatitude(),
                    e.gpsLongitude(),
                    e.speed()));
        }

        return Result.ok(new TrajectoryResponse(points, totalCount));
    }
}
