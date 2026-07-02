package com.aiot.application.fleet;

import com.aiot.domain.event.RiskLevel;
import com.aiot.domain.fleet.FleetAnalyticsService;
import com.aiot.domain.fleet.ReportGenerationService;
import com.aiot.domain.fleet.ScoringService;
import com.aiot.domain.model.Driver;
import com.aiot.domain.model.TimeRange;
import com.aiot.domain.model.Trip;
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
import com.aiot.infra.persistence.AlertProjectionEntity;
import com.aiot.infra.persistence.FleetDashboardProjectionEntity;
import com.aiot.infra.persistence.TrajectoryProjectionEntity;
import com.aiot.infra.repository.AlertProjectionJpaRepository;
import com.aiot.infra.repository.FleetDashboardProjectionJpaRepository;
import com.aiot.infra.repository.TrajectoryProjectionJpaRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class FleetManagementServiceImpl implements IFleetManagementService {

    private final FleetAnalyticsService fleetAnalyticsService;
    private final ReportGenerationService reportGenerationService;
    private final ScoringService scoringService;
    private final FleetDashboardProjectionJpaRepository fleetDashboardRepo;
    private final AlertProjectionJpaRepository alertRepo;
    private final TrajectoryProjectionJpaRepository trajectoryRepo;
    private final NotificationPort notificationPort;
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final TripRepository tripRepository;

    public FleetManagementServiceImpl(
            FleetAnalyticsService fleetAnalyticsService,
            ReportGenerationService reportGenerationService,
            ScoringService scoringService,
            FleetDashboardProjectionJpaRepository fleetDashboardRepo,
            AlertProjectionJpaRepository alertRepo,
            TrajectoryProjectionJpaRepository trajectoryRepo,
            NotificationPort notificationPort,
            VehicleRepository vehicleRepository,
            DriverRepository driverRepository,
            TripRepository tripRepository) {
        this.fleetAnalyticsService = fleetAnalyticsService;
        this.reportGenerationService = reportGenerationService;
        this.scoringService = scoringService;
        this.fleetDashboardRepo = fleetDashboardRepo;
        this.alertRepo = alertRepo;
        this.trajectoryRepo = trajectoryRepo;
        this.notificationPort = notificationPort;
        this.vehicleRepository = vehicleRepository;
        this.driverRepository = driverRepository;
        this.tripRepository = tripRepository;
    }

    @Override
    public Result<GetFatigueDistributionResponse, AppError> getFatigueDistribution(String fleetId) {
        Result<FleetAnalyticsService.FatigueDistribution, AppError> result =
                fleetAnalyticsService.getFleetFatigueDistribution(fleetId);
        if (result.isErr()) {
            return Result.err(result.unwrapErr());
        }
        FleetAnalyticsService.FatigueDistribution domainResult = result.unwrap();
        List<FleetDashboardProjectionEntity> dashboardEntries =
                fleetDashboardRepo.findByFleetId(fleetId);
        String freshness = dashboardEntries.isEmpty()
                ? "unknown"
                : dashboardEntries.get(0).getUpdatedAt().toString();
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
                List<Trip> trips = tripRepository.findByDriverId("");
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
                .filter(s -> {
                    List<AlertProjectionEntity> alerts = alertRepo.findByDriverId(s.driverId());
                    return alerts.stream().anyMatch(a -> fleetId.equals(a.getFleetId()));
                })
                .collect(Collectors.toList());

        long totalCount = fleetFiltered.size();
        int fromIndex = (page - 1) * size;
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
                    reportGenerationService.generateDriverReport(driverId.id());
            if (result.isErr()) {
                return Result.err(result.unwrapErr());
            }
            ReportGenerationService.DriverReport report = result.unwrap();
            boolean isEmpty = report.tripCount() == 0;
            return Result.ok(new GenerateReportResponse(
                    reportId, report, null, isEmpty));
        }

        if ("FLEET".equalsIgnoreCase(reportType)) {
            Result<ReportGenerationService.FleetReport, AppError> result =
                    reportGenerationService.generateFleetReport(driverId.id());
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
        List<TrajectoryProjectionEntity> allPoints =
                trajectoryRepo.findAll().stream()
                        .filter(p -> vehicleId.id().equals(p.getVehicleId()))
                        .collect(Collectors.toList());

        long totalCount = allPoints.size();
        int fromIndex = (page - 1) * size;
        if (fromIndex >= allPoints.size()) {
            return Result.ok(new TrajectoryResponse(Collections.emptyList(), totalCount));
        }
        int toIndex = Math.min(fromIndex + size, allPoints.size());
        List<TrajectoryProjectionEntity> paged = allPoints.subList(fromIndex, toIndex);

        List<TrajectoryPoint> points = new ArrayList<>();
        for (TrajectoryProjectionEntity e : paged) {
            long timestamp = e.getRecordedAt() != null
                    ? e.getRecordedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    : 0L;
            points.add(new TrajectoryPoint(
                    timestamp,
                    e.getGpsLatitude() != null ? e.getGpsLatitude() : 0.0,
                    e.getGpsLongitude() != null ? e.getGpsLongitude() : 0.0,
                    e.getSpeed() != null ? e.getSpeed() : 0.0));
        }

        return Result.ok(new TrajectoryResponse(points, totalCount));
    }
}
