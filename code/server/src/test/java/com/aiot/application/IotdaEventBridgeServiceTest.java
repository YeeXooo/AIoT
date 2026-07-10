package com.aiot.application;

import com.aiot.domain.event.*;
import com.aiot.domain.model.Trip;
import com.aiot.domain.model.Vehicle;
import com.aiot.domain.repository.TripRepository;
import com.aiot.domain.repository.VehicleRepository;
import com.aiot.domain.shared.*;
import com.aiot.interfaces.websocket.FleetWebSocketHandler;
import com.aiot.interfaces.websocket.GuardianshipWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IotdaEventBridgeServiceTest {

    @Mock
    private DomainEventPublisher eventPublisher;
    @Mock
    private TripRepository tripRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private GuardianshipWebSocketHandler guardianshipWs;
    @Mock
    private FleetWebSocketHandler fleetWs;
    @Mock
    private LatestSensorDataStore sensorDataStore;

    private IotdaEventBridgeService bridge;

    private final TripId tripId = new TripId("t001");
    private final DriverId driverId = new DriverId("d001");
    private final VehicleId vehicleId = new VehicleId("v001");
    private final String deviceId = "device_001";

    @BeforeEach
    void setUp() {
        bridge = new IotdaEventBridgeService(
                eventPublisher, tripRepository, vehicleRepository, guardianshipWs, fleetWs, sensorDataStore);
    }

    @Test
    void onSafetyAlertDetected_shouldPublishRiskDeterminedEvent() {
        SafetyAlertDetectedEvent event = new SafetyAlertDetectedEvent(
                tripId, deviceId, AlertType.SYSTEM_RISK, RiskLevel.L3_CRITICAL,
                41.0, 123.0, Instant.now(), "SOS alert from IoTDA");

        bridge.onSafetyAlertDetected(event);

        ArgumentCaptor<RiskDeterminedEvent> captor = ArgumentCaptor.forClass(RiskDeterminedEvent.class);
        verify(eventPublisher).publish(captor.capture());

        RiskDeterminedEvent riskEvent = captor.getValue();
        assertEquals(tripId, riskEvent.tripId());
        assertEquals(RiskLevel.L3_CRITICAL, riskEvent.riskLevel());
        assertEquals(AlertType.SYSTEM_RISK, riskEvent.alertType());
        assertEquals("SOS alert from IoTDA", riskEvent.anomalyDescription());
    }

    @Test
    void onAlertTriggered_shouldPushToWebSocket() {
        stubExistingTrip();
        stubExistingVehicle();

        AlertTriggeredEvent event = new AlertTriggeredEvent(
                AlertId.generate(), tripId, AlertType.FATIGUE, RiskLevel.L3_CRITICAL,
                41.0, 123.0, Instant.now());

        bridge.onAlertTriggered(event);

        verify(guardianshipWs, atLeastOnce()).broadcastAlertToSubscribers(
                eq("d001"), any());
        verify(guardianshipWs).broadcastDriverStatus(eq("d001"), any());
        verify(fleetWs).broadcastL3Alert(eq("fleet-east-1"), eq("d001"), eq("v001"),
                eq("FATIGUE"), any());
    }

    @Test
    void onAlertTriggered_shouldSkipWebSocket_whenTripNotFound() {
        when(tripRepository.findById(tripId.id())).thenReturn(Optional.empty());

        AlertTriggeredEvent event = new AlertTriggeredEvent(
                AlertId.generate(), tripId, AlertType.FATIGUE, RiskLevel.L2_WARNING,
                0.0, 0.0, Instant.now());

        bridge.onAlertTriggered(event);

        verify(guardianshipWs, never()).broadcastAlertToSubscribers(anyString(), any());
        verify(fleetWs, never()).broadcastL3Alert(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void onAlertTriggered_shouldNotCallFleetBroadcast_whenNotL3() {
        stubExistingTrip();

        AlertTriggeredEvent event = new AlertTriggeredEvent(
                AlertId.generate(), tripId, AlertType.SUDDEN_BRAKING, RiskLevel.L2_WARNING,
                41.0, 123.0, Instant.now());

        bridge.onAlertTriggered(event);

        verify(guardianshipWs).broadcastAlertToSubscribers(eq("d001"), any());
        verify(fleetWs, never()).broadcastL3Alert(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void onSensorDataCollected_shouldNotThrow() {
        SensorDataCollected event = new SensorDataCollected(tripId, deviceId,
                java.util.List.of());

        assertDoesNotThrow(() -> bridge.onSensorDataCollected(event));
    }

    @Test
    void onPhysiologicalDataUpdated_shouldNotThrow() {
        var snapshot = new com.aiot.domain.model.PhysiologicalSnapshot(
                Instant.now(), 72, 98.5, null, null, null, null, null, null);
        PhysiologicalDataUpdated event = new PhysiologicalDataUpdated(tripId, deviceId, snapshot);

        assertDoesNotThrow(() -> bridge.onPhysiologicalDataUpdated(event));
    }

    @Test
    void onVehicleStateUpdated_shouldNotThrow() {
        var state = new com.aiot.domain.model.VehicleStateSnapshot(
                Instant.now(), null, null, null, null, null, 41.0, 123.0);
        VehicleStateUpdated event = new VehicleStateUpdated(tripId, deviceId, state);

        assertDoesNotThrow(() -> bridge.onVehicleStateUpdated(event));
    }

    @Test
    void onBehaviorCountersUpdated_shouldNotThrow() {
        var counters = com.aiot.domain.model.DrivingBehaviorCounters.of(10, 5, 0, 0, 0, 3);
        BehaviorCountersUpdated event = new BehaviorCountersUpdated(tripId, deviceId, counters);

        assertDoesNotThrow(() -> bridge.onBehaviorCountersUpdated(event));
    }

    private void stubExistingTrip() {
        Trip trip = mockTrip();
        when(tripRepository.findById(tripId.id())).thenReturn(Optional.of(trip));
    }

    private void stubExistingVehicle() {
        Vehicle vehicle = mock(Vehicle.class);
        when(vehicle.fleetId()).thenReturn(Optional.of("fleet-east-1"));
        when(vehicleRepository.findById(vehicleId.id())).thenReturn(Optional.of(vehicle));
    }

    private Trip mockTrip() {
        Trip trip = Trip.reconstitute(
                tripId, driverId, vehicleId,
                LocalDateTime.now().minusHours(1), null,
                10, 5, 80, 1,
                LocalDateTime.now().minusHours(1), LocalDateTime.now());
        return trip;
    }
}
