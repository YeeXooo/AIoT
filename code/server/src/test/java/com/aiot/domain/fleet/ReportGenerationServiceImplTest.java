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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportGenerationServiceImplTest {

    @Mock private VehicleRepository vehicleRepository;
    @Mock private DriverRepository driverRepository;
    @Mock private TripRepository tripRepository;
    @Mock private ScoringService scoringService;
    @Mock private DomainEventPublisher eventPublisher;

    private ReportGenerationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReportGenerationServiceImpl(vehicleRepository, driverRepository, tripRepository, scoringService, eventPublisher);
    }

    @Test
    void generateFleetReportReturnsReportForExistingFleet() {
        when(vehicleRepository.findByFleetId("fleet-1")).thenReturn(List.of(Vehicle.register("京A12345", "VIN001", "SN001")));
        Driver driver = Driver.create("张三", "13800000001");
        when(driverRepository.findAll()).thenReturn(List.of(driver));
        when(scoringService.calculateDriverCompositeScore(driver.driverId().id())).thenReturn(Result.ok(85.0));

        Result<ReportGenerationService.FleetReport, AppError> result = service.generateFleetReport("fleet-1");

        assertTrue(result.isOk());
        ReportGenerationService.FleetReport report = result.unwrap();
        assertEquals("fleet-1", report.fleetId());
        assertEquals(1, report.vehicleCount());
        assertEquals(1, report.driverCount());
        assertEquals(85.0, report.avgScore());
    }

    @Test
    void generateFleetReportReturnsErrorForEmptyFleet() {
        when(vehicleRepository.findByFleetId("fleet-unknown")).thenReturn(Collections.emptyList());

        Result<ReportGenerationService.FleetReport, AppError> result = service.generateFleetReport("fleet-unknown");

        assertTrue(result.isErr());
        assertEquals("NotFound", result.unwrapErr().code());
    }

    @Test
    void generateFleetReportHandlesScoringErrors() {
        when(vehicleRepository.findByFleetId("fleet-1")).thenReturn(List.of(Vehicle.register("京A12345", "VIN001", "SN001")));
        Driver driver = Driver.create("张三", "13800000001");
        when(driverRepository.findAll()).thenReturn(List.of(driver));
        when(scoringService.calculateDriverCompositeScore(driver.driverId().id())).thenReturn(Result.err(AppError.invalidState("error")));

        Result<ReportGenerationService.FleetReport, AppError> result = service.generateFleetReport("fleet-1");

        assertTrue(result.isOk());
        ReportGenerationService.FleetReport report = result.unwrap();
        assertEquals(100.0, report.avgScore());
        assertEquals(0, report.driverCount());
    }

    @Test
    void generateDriverReportReturnsReportForExistingDriver() {
        Driver driver = Driver.create("张三", "13800000001");
        DriverId driverId = driver.driverId();
        TimeRange timeRange = new TimeRange(Instant.now().minusSeconds(3600), Instant.now());
        when(driverRepository.findById(driverId.id())).thenReturn(Optional.of(driver));
        when(tripRepository.findByDriverId(driverId.id())).thenReturn(Collections.emptyList());
        when(scoringService.calculateDriverCompositeScore(driverId.id())).thenReturn(Result.ok(90.0));

        Result<ReportGenerationService.DriverReport, AppError> result = service.generateDriverReport(driverId, timeRange);

        assertTrue(result.isOk());
        ReportGenerationService.DriverReport report = result.unwrap();
        assertEquals(driverId.id(), report.driverId());
        assertEquals("张三", report.driverName());
        assertEquals(90.0, report.score());
    }

    @Test
    void generateDriverReportReturnsErrorForMissingDriver() {
        DriverId driverId = new DriverId("nonexistent");
        TimeRange timeRange = new TimeRange(Instant.now().minusSeconds(3600), Instant.now());
        when(driverRepository.findById(driverId.id())).thenReturn(Optional.empty());

        Result<ReportGenerationService.DriverReport, AppError> result = service.generateDriverReport(driverId, timeRange);

        assertTrue(result.isErr());
        assertEquals("NotFound", result.unwrapErr().code());
    }

    @Test
    void generateDriverReportPublishesWarningWhenScoreBelowThreshold() {
        Driver driver = Driver.create("张三", "13800000001");
        DriverId driverId = driver.driverId();
        TimeRange timeRange = new TimeRange(Instant.now().minusSeconds(3600), Instant.now());
        when(driverRepository.findById(driverId.id())).thenReturn(Optional.of(driver));
        when(tripRepository.findByDriverId(driverId.id())).thenReturn(Collections.emptyList());
        when(scoringService.calculateDriverCompositeScore(driverId.id())).thenReturn(Result.ok(30.0));

        Result<ReportGenerationService.DriverReport, AppError> result = service.generateDriverReport(driverId, timeRange);

        assertTrue(result.isOk());
        assertEquals(30.0, result.unwrap().score());
        verify(eventPublisher).publish(any(PerformanceWarningEvent.class));
    }

    @Test
    void generateDriverReportDoesNotPublishWarningWhenScoreAboveThreshold() {
        Driver driver = Driver.create("张三", "13800000001");
        DriverId driverId = driver.driverId();
        TimeRange timeRange = new TimeRange(Instant.now().minusSeconds(3600), Instant.now());
        when(driverRepository.findById(driverId.id())).thenReturn(Optional.of(driver));
        when(tripRepository.findByDriverId(driverId.id())).thenReturn(Collections.emptyList());
        when(scoringService.calculateDriverCompositeScore(driverId.id())).thenReturn(Result.ok(80.0));

        service.generateDriverReport(driverId, timeRange);

        verify(eventPublisher, never()).publish(any(PerformanceWarningEvent.class));
    }

    @Test
    void generateDriverReportReturnsErrorWhenScoringFails() {
        Driver driver = Driver.create("张三", "13800000001");
        DriverId driverId = driver.driverId();
        TimeRange timeRange = new TimeRange(Instant.now().minusSeconds(3600), Instant.now());
        when(driverRepository.findById(driverId.id())).thenReturn(Optional.of(driver));
        when(tripRepository.findByDriverId(driverId.id())).thenReturn(Collections.emptyList());
        when(scoringService.calculateDriverCompositeScore(driverId.id())).thenReturn(Result.err(AppError.invalidState("scoring error")));

        Result<ReportGenerationService.DriverReport, AppError> result = service.generateDriverReport(driverId, timeRange);

        assertTrue(result.isErr());
    }
}
