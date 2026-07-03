package com.aiot.domain.fleet;

import com.aiot.domain.event.RiskLevel;
import com.aiot.domain.model.Driver;
import com.aiot.domain.model.DrivingBehaviorCounters;
import com.aiot.domain.model.Trip;
import com.aiot.domain.model.Vehicle;
import com.aiot.domain.repository.DriverRepository;
import com.aiot.domain.repository.TripRepository;
import com.aiot.domain.repository.VehicleRepository;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FleetAnalyticsServiceImplTest {

    @Mock private VehicleRepository vehicleRepository;
    @Mock private DriverRepository driverRepository;
    @Mock private TripRepository tripRepository;
    @Mock private ScoringService scoringService;

    private FleetAnalyticsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FleetAnalyticsServiceImpl(vehicleRepository, driverRepository, tripRepository, scoringService);
    }

    @Test
    void getFleetFatigueDistributionReturnsDistributionForExistingFleet() {
        when(vehicleRepository.findByFleetId("fleet-1")).thenReturn(List.of(Vehicle.register("京A12345", "VIN001", "SN001")));

        Result<FleetAnalyticsService.FatigueDistribution, AppError> result = service.getFleetFatigueDistribution("fleet-1");

        assertTrue(result.isOk());
        FleetAnalyticsService.FatigueDistribution dist = result.unwrap();
        assertNotNull(dist.distribution());
        assertTrue(dist.distribution().containsKey("LOW_FATIGUE"));
        assertTrue(dist.distribution().containsKey("MODERATE_FATIGUE"));
        assertTrue(dist.distribution().containsKey("HIGH_FATIGUE"));
    }

    @Test
    void getFleetFatigueDistributionReturnsErrorForEmptyFleet() {
        when(vehicleRepository.findByFleetId("fleet-unknown")).thenReturn(Collections.emptyList());

        Result<FleetAnalyticsService.FatigueDistribution, AppError> result = service.getFleetFatigueDistribution("fleet-unknown");

        assertTrue(result.isErr());
        assertEquals("NotFound", result.unwrapErr().code());
    }

    @Test
    void drillDownL1HintMatchesDriversWithNoTrips() {
        Driver driver = Driver.create("张三", "13800000001");
        when(driverRepository.findAll()).thenReturn(List.of(driver));
        when(tripRepository.findByDriverId(driver.driverId().id())).thenReturn(Collections.emptyList());
        when(scoringService.calculateDriverCompositeScore(driver.driverId().id())).thenReturn(Result.ok(85.0));

        Result<List<FleetAnalyticsService.DriverSummary>, AppError> result = service.drillDown(RiskLevel.L1_HINT);

        assertTrue(result.isOk());
        assertEquals(1, result.unwrap().size());
    }

    @Test
    void drillDownL2WarningMatchesDriversWithModerateEvents() {
        Driver driver = Driver.create("李四", "13800000002");
        DrivingBehaviorCounters counters = DrivingBehaviorCounters.of(3, 5, 0, 0, 0);
        Trip trip = Trip.start(driver.driverId(), com.aiot.domain.shared.VehicleId.generate(), java.time.LocalDateTime.now());
        trip.updateDrivingBehaviorCounters(counters);
        trip.end(java.time.LocalDateTime.now().plusHours(1));
        when(driverRepository.findAll()).thenReturn(List.of(driver));
        when(tripRepository.findByDriverId(driver.driverId().id())).thenReturn(List.of(trip));
        when(scoringService.calculateDriverCompositeScore(driver.driverId().id())).thenReturn(Result.ok(70.0));

        Result<List<FleetAnalyticsService.DriverSummary>, AppError> result = service.drillDown(RiskLevel.L2_WARNING);

        assertTrue(result.isOk());
        assertEquals(1, result.unwrap().size());
    }

    @Test
    void drillDownL3CriticalMatchesDriversWithHighEvents() {
        Driver driver = Driver.create("王五", "13800000003");
        DrivingBehaviorCounters counters = DrivingBehaviorCounters.of(10, 10, 0, 0, 0);
        Trip trip = Trip.start(driver.driverId(), com.aiot.domain.shared.VehicleId.generate(), java.time.LocalDateTime.now());
        trip.updateDrivingBehaviorCounters(counters);
        trip.end(java.time.LocalDateTime.now().plusHours(1));
        when(driverRepository.findAll()).thenReturn(List.of(driver));
        when(tripRepository.findByDriverId(driver.driverId().id())).thenReturn(List.of(trip));
        when(scoringService.calculateDriverCompositeScore(driver.driverId().id())).thenReturn(Result.ok(30.0));

        Result<List<FleetAnalyticsService.DriverSummary>, AppError> result = service.drillDown(RiskLevel.L3_CRITICAL);

        assertTrue(result.isOk());
        assertEquals(1, result.unwrap().size());
    }

    @Test
    void drillDownReturnsEmptyListWhenNoDriversMatch() {
        Driver driver = Driver.create("赵六", "13800000004");
        DrivingBehaviorCounters counters = DrivingBehaviorCounters.of(0, 0, 0, 0, 0);
        Trip trip = Trip.start(driver.driverId(), com.aiot.domain.shared.VehicleId.generate(), java.time.LocalDateTime.now());
        trip.updateDrivingBehaviorCounters(counters);
        trip.end(java.time.LocalDateTime.now().plusHours(1));
        when(driverRepository.findAll()).thenReturn(List.of(driver));
        when(tripRepository.findByDriverId(driver.driverId().id())).thenReturn(List.of(trip));

        Result<List<FleetAnalyticsService.DriverSummary>, AppError> result = service.drillDown(RiskLevel.L3_CRITICAL);

        assertTrue(result.isOk());
        assertTrue(result.unwrap().isEmpty());
    }

    @Test
    void drillDownHandlesScoringError() {
        Driver driver = Driver.create("孙七", "13800000005");
        DrivingBehaviorCounters counters = DrivingBehaviorCounters.of(10, 15, 0, 0, 0);
        Trip trip = Trip.start(driver.driverId(), com.aiot.domain.shared.VehicleId.generate(), java.time.LocalDateTime.now());
        trip.updateDrivingBehaviorCounters(counters);
        trip.end(java.time.LocalDateTime.now().plusHours(1));
        when(driverRepository.findAll()).thenReturn(List.of(driver));
        when(tripRepository.findByDriverId(driver.driverId().id())).thenReturn(List.of(trip));
        when(scoringService.calculateDriverCompositeScore(driver.driverId().id())).thenReturn(Result.err(AppError.invalidState("score error")));

        Result<List<FleetAnalyticsService.DriverSummary>, AppError> result = service.drillDown(RiskLevel.L3_CRITICAL);

        assertTrue(result.isOk());
        assertEquals(1, result.unwrap().size());
        assertEquals(0.0, result.unwrap().get(0).score());
    }
}
