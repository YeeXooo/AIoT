package com.aiot.application.fleet;

import com.aiot.domain.event.RiskLevel;
import com.aiot.domain.fleet.FleetAnalyticsService;
import com.aiot.domain.fleet.ReportGenerationService;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FleetManagementServiceImplTest {

    @Mock private FleetAnalyticsService fleetAnalyticsService;
    @Mock private ReportGenerationService reportGenerationService;
    @Mock private NotificationPort notificationPort;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private DriverRepository driverRepository;
    @Mock private TripRepository tripRepository;

    private FleetManagementServiceImpl service;
    private final DriverId driverId = new DriverId("drv-1");
    private final AccountId accountId = new AccountId("acc-1");
    private final String fleetId = "fleet-1";

    @BeforeEach
    void setUp() {
        service = new FleetManagementServiceImpl(
                fleetAnalyticsService, reportGenerationService,
                notificationPort, vehicleRepository,
                driverRepository, tripRepository);
    }

    @Test
    void getFatigueDistributionShouldReturnDistributionOnSuccess() {
        Map<String, Double> dist = Map.of("L1_HINT", 0.3, "L2_WARNING", 0.5, "L3_CRITICAL", 0.2);
        var domainResult = new FleetAnalyticsService.FatigueDistribution(dist);
        when(fleetAnalyticsService.getFleetFatigueDistribution(fleetId))
                .thenReturn(Result.ok(domainResult));

        var result = service.getFatigueDistribution(fleetId);

        assertTrue(result.isOk());
        assertEquals(dist, result.unwrap().distribution());
        assertNotNull(result.unwrap().dataFreshness());
    }

    @Test
    void getFatigueDistributionShouldUseFreshnessWhenAvailable() {
        Map<String, Double> dist = Map.of("L1_HINT", 0.5, "L2_WARNING", 0.5);
        var domainResult = new FleetAnalyticsService.FatigueDistribution(dist);
        when(fleetAnalyticsService.getFleetFatigueDistribution(fleetId))
                .thenReturn(Result.ok(domainResult));
        LocalDateTime freshTime = LocalDateTime.of(2025, 1, 1, 12, 0);
        service.updateFleetFreshness(fleetId, freshTime);

        var result = service.getFatigueDistribution(fleetId);

        assertTrue(result.isOk());
        assertEquals(freshTime.toString(), result.unwrap().dataFreshness());
    }

    @Test
    void getFatigueDistributionShouldReturnErrorWhenAnalyticsFails() {
        var error = AppError.notFound("Fleet", fleetId);
        when(fleetAnalyticsService.getFleetFatigueDistribution(fleetId))
                .thenReturn(Result.err(error));

        var result = service.getFatigueDistribution(fleetId);

        assertTrue(result.isErr());
        assertEquals(error, result.unwrapErr());
    }

    @Test
    void getOfflineVehiclesShouldReturnOfflineVehicles() {
        var online = Vehicle.register("京A12345", "VIN001", "TERM001");
        var offline = Vehicle.register("京B67890", "VIN002", "TERM002");
        offline.updateMonitoringOffline(true);
        when(vehicleRepository.findByFleetId(fleetId)).thenReturn(List.of(online, offline));

        var result = service.getOfflineVehicles(fleetId);

        assertTrue(result.isOk());
        assertEquals(1, result.unwrap().vehicles().size());
        assertEquals("京B67890", result.unwrap().vehicles().get(0).licensePlate());
    }

    @Test
    void getOfflineVehiclesShouldReturnEmptyWhenFleetHasNoVehicles() {
        when(vehicleRepository.findByFleetId(fleetId)).thenReturn(Collections.emptyList());

        var result = service.getOfflineVehicles(fleetId);

        assertTrue(result.isOk());
        assertTrue(result.unwrap().vehicles().isEmpty());
    }

    @Test
    void getOfflineVehiclesShouldReturnEmptyWhenAllOnline() {
        var vehicle = Vehicle.register("京A12345", "VIN001", "TERM001");
        when(vehicleRepository.findByFleetId(fleetId)).thenReturn(List.of(vehicle));

        var result = service.getOfflineVehicles(fleetId);

        assertTrue(result.isOk());
        assertTrue(result.unwrap().vehicles().isEmpty());
    }

    @Test
    void drillDownHighRiskShouldReturnDriversOnSuccess() {
        var summary = new FleetAnalyticsService.DriverSummary("drv-1", 85.5, "High fatigue risk");
        when(fleetAnalyticsService.drillDown(RiskLevel.L3_CRITICAL))
                .thenReturn(Result.ok(List.of(summary)));
        service.registerDriverFleet("drv-1", fleetId);

        var result = service.drillDownHighRisk(fleetId, "L3_CRITICAL", 0, 10);

        assertTrue(result.isOk());
        var resp = result.unwrap();
        assertEquals(1, resp.totalCount());
        assertEquals("drv-1", resp.drivers().get(0).driverId());
    }

    @Test
    void drillDownHighRiskShouldReturnErrorForInvalidRiskLevel() {
        var result = service.drillDownHighRisk(fleetId, "INVALID_LEVEL", 0, 10);

        assertTrue(result.isErr());
        assertTrue(result.unwrapErr().message().contains("Invalid risk level"));
    }

    @Test
    void drillDownHighRiskShouldReturnErrorWhenAnalyticsFails() {
        var error = AppError.invalidState("analytics error");
        when(fleetAnalyticsService.drillDown(RiskLevel.L2_WARNING)).thenReturn(Result.err(error));

        var result = service.drillDownHighRisk(fleetId, "L2_WARNING", 0, 10);

        assertTrue(result.isErr());
        assertEquals(error, result.unwrapErr());
    }

    @Test
    void drillDownHighRiskShouldFilterByFleetAndIncludeDriverName() {
        var summary = new FleetAnalyticsService.DriverSummary("drv-1", 75.0, "Some risk");
        when(fleetAnalyticsService.drillDown(RiskLevel.L2_WARNING))
                .thenReturn(Result.ok(List.of(summary)));
        service.registerDriverFleet("drv-1", fleetId);
        var driver = Driver.create("John", "13800000002");
        when(driverRepository.findById("drv-1")).thenReturn(Optional.of(driver));

        var result = service.drillDownHighRisk(fleetId, "L2_WARNING", 0, 10);

        assertTrue(result.isOk());
        assertEquals("John", result.unwrap().drivers().get(0).driverName());
    }

    @Test
    void drillDownHighRiskShouldReturnUnknownDriverNameWhenNotFound() {
        var summary = new FleetAnalyticsService.DriverSummary("drv-1", 75.0, "Some risk");
        when(fleetAnalyticsService.drillDown(RiskLevel.L2_WARNING))
                .thenReturn(Result.ok(List.of(summary)));
        service.registerDriverFleet("drv-1", fleetId);
        when(driverRepository.findById("drv-1")).thenReturn(Optional.empty());

        var result = service.drillDownHighRisk(fleetId, "L2_WARNING", 0, 10);

        assertTrue(result.isOk());
        assertEquals("Unknown", result.unwrap().drivers().get(0).driverName());
    }

    @Test
    void drillDownHighRiskShouldHandlePaginationBeyondRange() {
        var summary = new FleetAnalyticsService.DriverSummary("drv-1", 85.5, "Risk");
        when(fleetAnalyticsService.drillDown(RiskLevel.L3_CRITICAL))
                .thenReturn(Result.ok(List.of(summary)));
        service.registerDriverFleet("drv-1", fleetId);

        var result = service.drillDownHighRisk(fleetId, "L3_CRITICAL", 5, 10);

        assertTrue(result.isOk());
        assertTrue(result.unwrap().drivers().isEmpty());
        assertEquals(1, result.unwrap().totalCount());
    }

    @Test
    void drillDownHighRiskShouldExcludeDriversNotInFleet() {
        var summary = new FleetAnalyticsService.DriverSummary("drv-1", 85.5, "Risk");
        when(fleetAnalyticsService.drillDown(RiskLevel.L3_CRITICAL))
                .thenReturn(Result.ok(List.of(summary)));

        var result = service.drillDownHighRisk(fleetId, "L3_CRITICAL", 0, 10);

        assertTrue(result.isOk());
        assertEquals(0, result.unwrap().totalCount());
    }

    @Test
    void generateReportShouldReturnDriverReportOnSuccess() {
        TimeRange timeRange = new TimeRange(
                Instant.now().minus(3600, java.time.temporal.ChronoUnit.SECONDS), Instant.now());
        var domainReport = new ReportGenerationService.DriverReport(
                "drv-1", "John", 85.0, 5, "Good performance");
        when(reportGenerationService.generateDriverReport(driverId, timeRange))
                .thenReturn(Result.ok(domainReport));

        var result = service.generateReport(driverId, timeRange, "DRIVER");

        assertTrue(result.isOk());
        var resp = result.unwrap();
        assertNotNull(resp.reportId());
        assertNotNull(resp.reportData());
        assertFalse(resp.isEmpty());
    }

    @Test
    void generateReportShouldReturnEmptyDriverReportWhenZeroTrips() {
        TimeRange timeRange = new TimeRange(
                Instant.now().minus(3600, java.time.temporal.ChronoUnit.SECONDS), Instant.now());
        var domainReport = new ReportGenerationService.DriverReport(
                "drv-1", "John", 0.0, 0, "No trips");
        when(reportGenerationService.generateDriverReport(driverId, timeRange))
                .thenReturn(Result.ok(domainReport));

        var result = service.generateReport(driverId, timeRange, "DRIVER");

        assertTrue(result.isOk());
        assertTrue(result.unwrap().isEmpty());
    }

    @Test
    void generateReportShouldReturnFleetReportOnSuccess() {
        TimeRange timeRange = new TimeRange(
                Instant.now().minus(3600, java.time.temporal.ChronoUnit.SECONDS), Instant.now());
        var domainReport = new ReportGenerationService.FleetReport(
                fleetId, 10, 25, 78.5, "Average performance");
        when(reportGenerationService.generateFleetReport(fleetId))
                .thenReturn(Result.ok(domainReport));
        Trip trip = Trip.start(driverId, new VehicleId("veh-1"), LocalDateTime.now());
        when(tripRepository.findByDriverId(driverId.id())).thenReturn(List.of(trip));
        var vehicle = Vehicle.register("京A12345", "VIN001", "TERM001");
        vehicle.updateFleetId(fleetId);
        when(vehicleRepository.findById("veh-1")).thenReturn(Optional.of(vehicle));

        var result = service.generateReport(driverId, timeRange, "FLEET");

        assertTrue(result.isOk());
        var resp = result.unwrap();
        assertNotNull(resp.reportId());
        assertNotNull(resp.reportData());
        assertFalse(resp.isEmpty());
    }

    @Test
    void generateReportShouldReturnEmptyFleetReportWhenZeroVehicles() {
        TimeRange timeRange = new TimeRange(
                Instant.now().minus(3600, java.time.temporal.ChronoUnit.SECONDS), Instant.now());
        var domainReport = new ReportGenerationService.FleetReport(
                fleetId, 0, 0, 0.0, "Empty fleet");
        when(reportGenerationService.generateFleetReport(fleetId))
                .thenReturn(Result.ok(domainReport));
        Trip trip = Trip.start(driverId, new VehicleId("veh-1"), LocalDateTime.now());
        when(tripRepository.findByDriverId(driverId.id())).thenReturn(List.of(trip));
        var vehicle = Vehicle.register("京A12345", "VIN001", "TERM001");
        vehicle.updateFleetId(fleetId);
        when(vehicleRepository.findById("veh-1")).thenReturn(Optional.of(vehicle));

        var result = service.generateReport(driverId, timeRange, "FLEET");

        assertTrue(result.isOk());
        assertTrue(result.unwrap().isEmpty());
    }

    @Test
    void generateReportShouldReturnErrorForUnknownReportType() {
        TimeRange timeRange = new TimeRange(
                Instant.now().minus(3600, java.time.temporal.ChronoUnit.SECONDS), Instant.now());

        var result = service.generateReport(driverId, timeRange, "UNKNOWN");

        assertTrue(result.isErr());
        assertTrue(result.unwrapErr().message().contains("Unsupported report type"));
    }

    @Test
    void generateReportShouldReturnErrorWhenDriverReportGenerationFails() {
        TimeRange timeRange = new TimeRange(
                Instant.now().minus(3600, java.time.temporal.ChronoUnit.SECONDS), Instant.now());
        var error = AppError.invalidState("report generation failed");
        when(reportGenerationService.generateDriverReport(driverId, timeRange))
                .thenReturn(Result.err(error));

        var result = service.generateReport(driverId, timeRange, "DRIVER");

        assertTrue(result.isErr());
        assertEquals(error, result.unwrapErr());
    }

    @Test
    void generateReportShouldReturnErrorWhenFleetReportGenerationFails() {
        TimeRange timeRange = new TimeRange(
                Instant.now().minus(3600, java.time.temporal.ChronoUnit.SECONDS), Instant.now());
        var error = AppError.invalidState("fleet report generation failed");
        Trip trip = Trip.start(driverId, new VehicleId("veh-1"), LocalDateTime.now());
        when(tripRepository.findByDriverId(driverId.id())).thenReturn(List.of(trip));
        var vehicle = Vehicle.register("京A12345", "VIN001", "TERM001");
        vehicle.updateFleetId(fleetId);
        when(vehicleRepository.findById("veh-1")).thenReturn(Optional.of(vehicle));
        when(reportGenerationService.generateFleetReport(fleetId)).thenReturn(Result.err(error));

        var result = service.generateReport(driverId, timeRange, "FLEET");

        assertTrue(result.isErr());
        assertEquals(error, result.unwrapErr());
    }

    @Test
    void subscribePerformanceWarningShouldReturnSubscriptionOnSuccess() throws Exception {
        var result = service.subscribePerformanceWarning(accountId, fleetId);

        assertTrue(result.isOk());
        assertNotNull(result.unwrap().subscriptionId());
        verify(notificationPort).pushNotification(eq(accountId), any(NotificationPort.NotificationPayload.class));
    }

    @Test
    void subscribePerformanceWarningShouldReturnErrorWhenNotificationFails() throws Exception {
        doThrow(new NotificationPort.NotificationException.DeliveryFailedException("delivery failed"))
                .when(notificationPort).pushNotification(any(), any());

        var result = service.subscribePerformanceWarning(accountId, fleetId);

        assertTrue(result.isErr());
        assertTrue(result.unwrapErr().message().contains("Failed to subscribe"));
    }

    @Test
    void queryVehicleTrajectoryShouldReturnPointsOnSuccess() {
        VehicleId vehicleId = new VehicleId("veh-1");
        TimeRange timeRange = new TimeRange(
                Instant.now().minus(3600, java.time.temporal.ChronoUnit.SECONDS), Instant.now());
        var point = new FleetManagementServiceImpl.InMemoryTrajectoryPoint(
                1000L, 39.9042, 116.4074, 60.5);
        service.storeTrajectoryPoint("veh-1", point);

        var result = service.queryVehicleTrajectory(vehicleId, timeRange, 0, 10);

        assertTrue(result.isOk());
        assertEquals(1, result.unwrap().totalCount());
        var tp = result.unwrap().points().get(0);
        assertEquals(39.9042, tp.lat());
        assertEquals(116.4074, tp.lng());
        assertEquals(60.5, tp.speed());
    }

    @Test
    void queryVehicleTrajectoryShouldReturnEmptyForUnknownVehicle() {
        VehicleId vehicleId = new VehicleId("unknown");
        TimeRange timeRange = new TimeRange(
                Instant.now().minus(3600, java.time.temporal.ChronoUnit.SECONDS), Instant.now());

        var result = service.queryVehicleTrajectory(vehicleId, timeRange, 0, 10);

        assertTrue(result.isOk());
        assertTrue(result.unwrap().points().isEmpty());
        assertEquals(0, result.unwrap().totalCount());
    }

    @Test
    void queryVehicleTrajectoryShouldHandlePaginationBeyondRange() {
        VehicleId vehicleId = new VehicleId("veh-1");
        TimeRange timeRange = new TimeRange(
                Instant.now().minus(3600, java.time.temporal.ChronoUnit.SECONDS), Instant.now());
        var point = new FleetManagementServiceImpl.InMemoryTrajectoryPoint(
                1000L, 39.9, 116.4, 55.0);
        service.storeTrajectoryPoint("veh-1", point);

        var result = service.queryVehicleTrajectory(vehicleId, timeRange, 5, 10);

        assertTrue(result.isOk());
        assertTrue(result.unwrap().points().isEmpty());
        assertEquals(1, result.unwrap().totalCount());
    }

    @Test
    void queryVehicleTrajectoryShouldPaginateCorrectly() {
        VehicleId vehicleId = new VehicleId("veh-1");
        TimeRange timeRange = new TimeRange(
                Instant.now().minus(3600, java.time.temporal.ChronoUnit.SECONDS), Instant.now());
        for (int i = 0; i < 5; i++) {
            service.storeTrajectoryPoint("veh-1",
                    new FleetManagementServiceImpl.InMemoryTrajectoryPoint(
                            1000L + i, 39.9 + i * 0.01, 116.4 + i * 0.01, 50.0 + i));
        }

        var result = service.queryVehicleTrajectory(vehicleId, timeRange, 0, 3);

        assertTrue(result.isOk());
        assertEquals(3, result.unwrap().points().size());
        assertEquals(5, result.unwrap().totalCount());
    }
}
