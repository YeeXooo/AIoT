package com.aiot.application.risk;

import com.aiot.domain.event.AlertType;
import com.aiot.domain.event.LifeDetectedEvent;
import com.aiot.domain.event.RiskDeterminedEvent;
import com.aiot.domain.event.RiskLevel;
import com.aiot.domain.model.DetectionWindow;
import com.aiot.domain.model.Driver;
import com.aiot.domain.model.SensorReading;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiskMonitoringServiceImplTest {

    @Mock private RiskDeterminationService riskDeterminationService;
    @Mock private LifeDetectionService lifeDetectionService;
    @Mock private TripRepository tripRepository;
    @Mock private DriverRepository driverRepository;

    private RiskMonitoringServiceImpl service;
    private final DriverId driverId = new DriverId("drv-1");
    private final VehicleId vehicleId = new VehicleId("veh-1");

    @BeforeEach
    void setUp() {
        service = new RiskMonitoringServiceImpl(
                riskDeterminationService, lifeDetectionService,
                tripRepository, driverRepository);
    }

    private SensorReading createSensorReading(SensorReading.SensorType type, TripId tripId) {
        return new SensorReading(type, Instant.now(), tripId, Map.of("PERCLOS", 0.8));
    }

    @Test
    void startMonitoringSessionShouldReturnSessionWithActiveTrip() {
        var driver = Driver.create("TestDriver", "13800000002");
        var trip = Trip.start(driverId, vehicleId, LocalDateTime.now());
        when(driverRepository.findById(driverId.id())).thenReturn(Optional.of(driver));
        when(tripRepository.findActiveTrips()).thenReturn(List.of(trip));

        var result = service.startMonitoringSession(driverId, vehicleId);

        assertTrue(result.isOk());
        var resp = result.unwrap();
        assertNotNull(resp.sessionHandle());
        assertEquals("ACTIVE", resp.status());
        assertEquals(driverId.id(), resp.driverId());
        assertEquals(vehicleId.id(), resp.vehicleId());
    }

    @Test
    void startMonitoringSessionShouldReturnSessionWithoutTripWhenNoActiveTrip() {
        var driver = Driver.create("TestDriver", "13800000002");
        when(driverRepository.findById(driverId.id())).thenReturn(Optional.of(driver));
        when(tripRepository.findActiveTrips()).thenReturn(Collections.emptyList());

        var result = service.startMonitoringSession(driverId, vehicleId);

        assertTrue(result.isOk());
        assertNotNull(result.unwrap().sessionHandle());
    }

    @Test
    void startMonitoringSessionShouldReturnSessionWhenTripMatchesDriverAndVehicle() {
        var driver = Driver.create("TestDriver", "13800000002");
        var otherTrip = Trip.start(new DriverId("other-drv"), new VehicleId("other-veh"), LocalDateTime.now());
        when(driverRepository.findById(driverId.id())).thenReturn(Optional.of(driver));
        when(tripRepository.findActiveTrips()).thenReturn(List.of(otherTrip));

        var result = service.startMonitoringSession(driverId, vehicleId);

        assertTrue(result.isOk());
    }

    @Test
    void startMonitoringSessionShouldReturnErrorWhenDriverNotFound() {
        when(driverRepository.findById(driverId.id())).thenReturn(Optional.empty());

        var result = service.startMonitoringSession(driverId, vehicleId);

        assertTrue(result.isErr());
        assertTrue(result.unwrapErr().message().contains("not found"));
    }

    @Test
    void startMonitoringSessionShouldReturnErrorWhenDriverNotActive() {
        var driver = Driver.create("TestDriver", "13800000002");
        driver.deactivate();
        when(driverRepository.findById(driverId.id())).thenReturn(Optional.of(driver));

        var result = service.startMonitoringSession(driverId, vehicleId);

        assertTrue(result.isErr());
        assertTrue(result.unwrapErr().message().contains("not active"));
    }

    @Test
    void processSensorReadingShouldReturnOkAndCreateAlerts() {
        var driver = Driver.create("TestDriver", "13800000002");
        when(driverRepository.findById(driverId.id())).thenReturn(Optional.of(driver));
        when(tripRepository.findActiveTrips()).thenReturn(Collections.emptyList());

        var sessionResult = service.startMonitoringSession(driverId, vehicleId);
        var sessionHandle = sessionResult.unwrap().sessionHandle();

        TripId tripId = new TripId("trip-1");
        SensorReading reading = createSensorReading(SensorReading.SensorType.DMS_CAMERA, tripId);
        ActiveRiskSet riskSet = ActiveRiskSet.empty("trip-1");
        RiskDeterminedEvent event = new RiskDeterminedEvent(
                tripId, RiskLevel.L3_CRITICAL, AlertType.FATIGUE, Instant.now(), "High perclos");
        var determinationResult = new RiskDeterminationService.DeterminationResult(
                riskSet, List.of(event), Collections.emptyList());
        when(riskDeterminationService.executeStreamFusion(any(), any()))
                .thenReturn(Result.ok(determinationResult));

        var result = service.processSensorReading(sessionHandle, reading);

        assertTrue(result.isOk());
        verify(riskDeterminationService).executeStreamFusion(any(), any());
    }

    @Test
    void processSensorReadingShouldReturnErrorWhenSessionNotFound() {
        TripId tripId = new TripId("trip-1");
        SensorReading reading = createSensorReading(SensorReading.SensorType.DMS_CAMERA, tripId);

        var result = service.processSensorReading("nonexistent", reading);

        assertTrue(result.isErr());
        assertTrue(result.unwrapErr().message().contains("not found"));
    }

    @Test
    void processSensorReadingShouldReturnErrorWhenRiskDeterminationFails() {
        var driver = Driver.create("TestDriver", "13800000002");
        when(driverRepository.findById(driverId.id())).thenReturn(Optional.of(driver));
        when(tripRepository.findActiveTrips()).thenReturn(Collections.emptyList());

        var sessionResult = service.startMonitoringSession(driverId, vehicleId);
        var sessionHandle = sessionResult.unwrap().sessionHandle();

        TripId tripId = new TripId("trip-1");
        SensorReading reading = createSensorReading(SensorReading.SensorType.DMS_CAMERA, tripId);
        var error = AppError.invalidState("fusion failed");
        when(riskDeterminationService.executeStreamFusion(any(), any()))
                .thenReturn(Result.err(error));

        var result = service.processSensorReading(sessionHandle, reading);

        assertTrue(result.isErr());
        assertEquals(error, result.unwrapErr());
    }

    @Test
    void processSensorReadingShouldHandleMillimeterWaveRadarWithLifeDetection() {
        var driver = Driver.create("TestDriver", "13800000002");
        when(driverRepository.findById(driverId.id())).thenReturn(Optional.of(driver));
        when(tripRepository.findActiveTrips()).thenReturn(Collections.emptyList());

        var sessionResult = service.startMonitoringSession(driverId, vehicleId);
        var sessionHandle = sessionResult.unwrap().sessionHandle();

        TripId tripId = new TripId("trip-1");
        SensorReading radarReading = createSensorReading(SensorReading.SensorType.MILLIMETER_WAVE_RADAR, tripId);
        ActiveRiskSet riskSet = ActiveRiskSet.empty("standalone-drv-1");
        var determinationResult = new RiskDeterminationService.DeterminationResult(
                riskSet, Collections.emptyList(), Collections.emptyList());
        when(riskDeterminationService.executeStreamFusion(any(), any()))
                .thenReturn(Result.ok(determinationResult));

        DetectionWindow window = DetectionWindow.create(Duration.ofMinutes(5), Instant.now(), Duration.ofSeconds(30));
        LifeDetectedEvent lifeEvent = new LifeDetectedEvent(vehicleId, 0.95, Instant.now());
        var lifeResult = new LifeDetectionService.DetectionResult(window, lifeEvent);
        when(lifeDetectionService.evaluateLifeDetection(any(SensorReading.class),
                any(DetectionWindow.class), eq(vehicleId)))
                .thenReturn(Result.ok(lifeResult));

        var result = service.processSensorReading(sessionHandle, radarReading);

        assertTrue(result.isOk());
        verify(lifeDetectionService).evaluateLifeDetection(any(), any(), eq(vehicleId));
    }

    @Test
    void processSensorReadingShouldHandleMillimeterWaveRadarWithoutLifeDetected() {
        var driver = Driver.create("TestDriver", "13800000002");
        when(driverRepository.findById(driverId.id())).thenReturn(Optional.of(driver));
        when(tripRepository.findActiveTrips()).thenReturn(Collections.emptyList());

        var sessionResult = service.startMonitoringSession(driverId, vehicleId);
        var sessionHandle = sessionResult.unwrap().sessionHandle();

        TripId tripId = new TripId("trip-1");
        SensorReading radarReading = createSensorReading(SensorReading.SensorType.MILLIMETER_WAVE_RADAR, tripId);
        ActiveRiskSet riskSet = ActiveRiskSet.empty("standalone-drv-1");
        var determinationResult = new RiskDeterminationService.DeterminationResult(
                riskSet, Collections.emptyList(), Collections.emptyList());
        when(riskDeterminationService.executeStreamFusion(any(), any()))
                .thenReturn(Result.ok(determinationResult));

        DetectionWindow window = DetectionWindow.create(Duration.ofMinutes(5), Instant.now(), Duration.ofSeconds(30));
        var lifeResult = new LifeDetectionService.DetectionResult(window, null);
        when(lifeDetectionService.evaluateLifeDetection(any(SensorReading.class),
                any(DetectionWindow.class), eq(vehicleId)))
                .thenReturn(Result.ok(lifeResult));

        var result = service.processSensorReading(sessionHandle, radarReading);

        assertTrue(result.isOk());
    }

    @Test
    void processSensorReadingShouldHandleMillimeterWaveRadarWithLifeDetectionError() {
        var driver = Driver.create("TestDriver", "13800000002");
        when(driverRepository.findById(driverId.id())).thenReturn(Optional.of(driver));
        when(tripRepository.findActiveTrips()).thenReturn(Collections.emptyList());

        var sessionResult = service.startMonitoringSession(driverId, vehicleId);
        var sessionHandle = sessionResult.unwrap().sessionHandle();

        TripId tripId = new TripId("trip-1");
        SensorReading radarReading = createSensorReading(SensorReading.SensorType.MILLIMETER_WAVE_RADAR, tripId);
        ActiveRiskSet riskSet = ActiveRiskSet.empty("standalone-drv-1");
        var determinationResult = new RiskDeterminationService.DeterminationResult(
                riskSet, Collections.emptyList(), Collections.emptyList());
        when(riskDeterminationService.executeStreamFusion(any(), any()))
                .thenReturn(Result.ok(determinationResult));
        when(lifeDetectionService.evaluateLifeDetection(any(), any(), any()))
                .thenReturn(Result.err(AppError.invalidState("life detection error")));

        var result = service.processSensorReading(sessionHandle, radarReading);

        assertTrue(result.isOk());
    }

    @Test
    void getDriverRiskStatusShouldReturnStatusWithRisks() {
        var driver = Driver.create("TestDriver", "13800000002");
        when(driverRepository.findById(driverId.id())).thenReturn(Optional.of(driver));
        when(tripRepository.findActiveTrips()).thenReturn(Collections.emptyList());

        var sessionResult = service.startMonitoringSession(driverId, vehicleId);
        var sessionHandle = sessionResult.unwrap().sessionHandle();

        TripId tripId = new TripId("trip-1");
        SensorReading reading = createSensorReading(SensorReading.SensorType.DMS_CAMERA, tripId);
        ActiveRiskSet riskSet = ActiveRiskSet.empty("standalone-drv-1")
                .add(AlertType.FATIGUE, RiskLevel.L3_CRITICAL, Instant.now(), "High perclos");
        var event = new RiskDeterminedEvent(
                tripId, RiskLevel.L3_CRITICAL, AlertType.FATIGUE, Instant.now(), "High perclos");
        var determinationResult = new RiskDeterminationService.DeterminationResult(
                riskSet, List.of(event), Collections.emptyList());
        when(riskDeterminationService.executeStreamFusion(any(), any()))
                .thenReturn(Result.ok(determinationResult));
        when(driverRepository.findById(driverId.id())).thenReturn(Optional.of(driver));

        service.processSensorReading(sessionHandle, reading);

        when(driverRepository.findById(driverId.id())).thenReturn(Optional.of(driver));
        var result = service.getDriverRiskStatus(driverId);

        assertTrue(result.isOk());
        var resp = result.unwrap();
        assertEquals(driverId.id(), resp.driverId());
        assertNotNull(resp.statusColor());
        assertFalse(resp.activeRisks().isEmpty());
    }

    @Test
    void getDriverRiskStatusShouldReturnErrorWhenDriverNotFound() {
        when(driverRepository.findById(driverId.id())).thenReturn(Optional.empty());

        var result = service.getDriverRiskStatus(driverId);

        assertTrue(result.isErr());
    }

    @Test
    void getDriverRiskStatusShouldReturnGreenStatusWhenNoSessions() {
        var driver = Driver.create("TestDriver", "13800000002");
        when(driverRepository.findById(driverId.id())).thenReturn(Optional.of(driver));

        var result = service.getDriverRiskStatus(driverId);

        assertTrue(result.isOk());
        assertNotNull(result.unwrap().statusColor());
        assertTrue(result.unwrap().activeRisks().isEmpty());
    }

    @Test
    void queryAlertHistoryShouldReturnFilteredAlerts() {
        var driver = Driver.create("TestDriver", "13800000002");
        when(driverRepository.findById(driverId.id())).thenReturn(Optional.of(driver));
        when(tripRepository.findActiveTrips()).thenReturn(Collections.emptyList());

        var sessionResult = service.startMonitoringSession(driverId, vehicleId);
        var sessionHandle = sessionResult.unwrap().sessionHandle();

        TripId tripId = new TripId("trip-1");
        SensorReading reading = createSensorReading(SensorReading.SensorType.DMS_CAMERA, tripId);
        ActiveRiskSet riskSet = ActiveRiskSet.empty("standalone-drv-1")
                .add(AlertType.FATIGUE, RiskLevel.L3_CRITICAL, Instant.now(), "High fatigue");
        var event = new RiskDeterminedEvent(
                tripId, RiskLevel.L3_CRITICAL, AlertType.FATIGUE, Instant.now(), "High fatigue");
        var determinationResult = new RiskDeterminationService.DeterminationResult(
                riskSet, List.of(event), Collections.emptyList());
        when(riskDeterminationService.executeStreamFusion(any(), any()))
                .thenReturn(Result.ok(determinationResult));

        service.processSensorReading(sessionHandle, reading);

        var result = service.queryAlertHistory(driverId,
                LocalDateTime.now().minusDays(7), LocalDateTime.now().plusDays(1),
                null, null, 0, 10);

        assertTrue(result.isOk());
        assertFalse(result.unwrap().items().isEmpty());
        assertTrue(result.unwrap().totalCount() > 0);
    }

    @Test
    void queryAlertHistoryShouldReturnEmptyForOtherDriver() {
        var driver = Driver.create("TestDriver", "13800000002");
        when(driverRepository.findById(driverId.id())).thenReturn(Optional.of(driver));
        when(tripRepository.findActiveTrips()).thenReturn(Collections.emptyList());

        service.startMonitoringSession(driverId, vehicleId);

        var result = service.queryAlertHistory(new DriverId("other-drv"),
                LocalDateTime.now().minusDays(7), LocalDateTime.now().plusDays(1),
                null, null, 0, 10);

        assertTrue(result.isOk());
        assertEquals(0, result.unwrap().totalCount());
    }

    @Test
    void queryAlertHistoryShouldFilterByAlertType() {
        var driver = Driver.create("TestDriver", "13800000002");
        when(driverRepository.findById(driverId.id())).thenReturn(Optional.of(driver));
        when(tripRepository.findActiveTrips()).thenReturn(Collections.emptyList());

        var sessionResult = service.startMonitoringSession(driverId, vehicleId);
        var sessionHandle = sessionResult.unwrap().sessionHandle();

        TripId tripId = new TripId("trip-1");
        SensorReading reading = createSensorReading(SensorReading.SensorType.DMS_CAMERA, tripId);
        ActiveRiskSet riskSet = ActiveRiskSet.empty("standalone-drv-1");
        var event = new RiskDeterminedEvent(
                tripId, RiskLevel.L3_CRITICAL, AlertType.FATIGUE, Instant.now(), "High fatigue");
        var determinationResult = new RiskDeterminationService.DeterminationResult(
                riskSet, List.of(event), Collections.emptyList());
        when(riskDeterminationService.executeStreamFusion(any(), any()))
                .thenReturn(Result.ok(determinationResult));

        service.processSensorReading(sessionHandle, reading);

        var result = service.queryAlertHistory(driverId,
                LocalDateTime.now().minusDays(7), LocalDateTime.now().plusDays(1),
                "DISTRACTION", null, 0, 10);

        assertTrue(result.isOk());
        assertEquals(0, result.unwrap().totalCount());
    }

    @Test
    void queryAlertHistoryShouldHandlePaginationBeyondTotal() {
        var driver = Driver.create("TestDriver", "13800000002");
        when(driverRepository.findById(driverId.id())).thenReturn(Optional.of(driver));
        when(tripRepository.findActiveTrips()).thenReturn(Collections.emptyList());

        var sessionResult = service.startMonitoringSession(driverId, vehicleId);
        var sessionHandle = sessionResult.unwrap().sessionHandle();

        TripId tripId = new TripId("trip-1");
        SensorReading reading = createSensorReading(SensorReading.SensorType.DMS_CAMERA, tripId);
        ActiveRiskSet riskSet = ActiveRiskSet.empty("standalone-drv-1")
                .add(AlertType.FATIGUE, RiskLevel.L3_CRITICAL, Instant.now(), "High fatigue");
        var event = new RiskDeterminedEvent(
                tripId, RiskLevel.L3_CRITICAL, AlertType.FATIGUE, Instant.now(), "High fatigue");
        var determinationResult = new RiskDeterminationService.DeterminationResult(
                riskSet, List.of(event), Collections.emptyList());
        when(riskDeterminationService.executeStreamFusion(any(), any()))
                .thenReturn(Result.ok(determinationResult));

        service.processSensorReading(sessionHandle, reading);

        var result = service.queryAlertHistory(driverId,
                null, null, null, null, 10, 10);

        assertTrue(result.isOk());
        assertTrue(result.unwrap().items().isEmpty());
        assertTrue(result.unwrap().totalCount() > 0);
    }

    @Test
    void queryAlertHistoryShouldHandleNullTimeFilters() {
        var driver = Driver.create("TestDriver", "13800000002");
        when(driverRepository.findById(driverId.id())).thenReturn(Optional.of(driver));
        when(tripRepository.findActiveTrips()).thenReturn(Collections.emptyList());

        var sessionResult = service.startMonitoringSession(driverId, vehicleId);
        var sessionHandle = sessionResult.unwrap().sessionHandle();

        TripId tripId = new TripId("trip-1");
        SensorReading reading = createSensorReading(SensorReading.SensorType.DMS_CAMERA, tripId);
        ActiveRiskSet riskSet = ActiveRiskSet.empty("standalone-drv-1")
                .add(AlertType.DISTRACTION, RiskLevel.L2_WARNING, Instant.now(), "Distracted");
        var event = new RiskDeterminedEvent(
                tripId, RiskLevel.L2_WARNING, AlertType.DISTRACTION, Instant.now(), "Distracted");
        var determinationResult = new RiskDeterminationService.DeterminationResult(
                riskSet, List.of(event), Collections.emptyList());
        when(riskDeterminationService.executeStreamFusion(any(), any()))
                .thenReturn(Result.ok(determinationResult));

        service.processSensorReading(sessionHandle, reading);

        var result = service.queryAlertHistory(driverId,
                null, null, null, null, 0, 10);

        assertTrue(result.isOk());
        assertTrue(result.unwrap().totalCount() > 0);
    }
}
