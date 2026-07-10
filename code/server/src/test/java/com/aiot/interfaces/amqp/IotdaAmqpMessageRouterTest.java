package com.aiot.interfaces.amqp;

import com.aiot.application.LatestSensorDataStore;
import com.aiot.domain.event.*;
import com.aiot.domain.model.DrivingBehaviorCounters;
import com.aiot.domain.model.PhysiologicalSnapshot;
import com.aiot.domain.model.SensorReading;
import com.aiot.domain.model.Vehicle;
import com.aiot.domain.model.VehicleStateSnapshot;
import com.aiot.domain.repository.TripRepository;
import com.aiot.domain.repository.VehicleRepository;
import com.aiot.domain.shared.TripId;
import com.aiot.domain.shared.VehicleId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IotdaAmqpMessageRouterTest {

    @Mock
    private DomainEventPublisher eventPublisher;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private TripRepository tripRepository;
    @Mock
    private LatestSensorDataStore sensorDataStore;

    private IotdaAmqpMessageRouter router;

    private static final String NORMAL_JSON = """
            {
              "notify_data": {
                "header": {
                  "app_id": "abc",
                  "device_id": "6a44f1047f2e6c302f80df85_vehicle_safety",
                  "node_id": "vehicle_safety",
                  "product_id": "6a44f1047f2e6c302f80df85",
                  "gateway_id": "abc_gw"
                },
                "body": {
                  "services": [{
                    "service_id": "VehicleSafety",
                    "event_time": "20260703T183519Z",
                    "properties": {
                      "temp": 24.0, "humi": 60.0, "lux": 65,
                      "perclos": 0.09, "yawn": 0, "phone": 0,
                      "ax": 0.2, "ay": 0.0, "az": 9.8, "gx": 0.8, "gy": 0.7, "gz": 2.2,
                      "lat": 41.8028, "lon": 123.5497, "gps_fix": 1,
                      "hr": 72, "spo2": 98.5, "resting_hr": 65,
                      "hard_brake": 24, "hard_accel": 59, "sharp_turn": 32,
                      "radar_human": 1, "radar_range_lo": 0.5, "radar_range_hi": 2.0,
                      "risk": 0, "battery_mv": 3800, "pc_lvl": 0
                    }
                  }]
                }
              }
            }""";

    private static final String ALERT_JSON = """
            {
              "notify_data": {
                "header": {
                  "app_id": "abc",
                  "device_id": "device_alert_001",
                  "node_id": "alert_device",
                  "product_id": "prod123",
                  "gateway_id": "gw123"
                },
                "body": {
                  "services": [{
                    "service_id": "VehicleSafety",
                    "event_time": "20260703T183519Z",
                    "properties": {
                      "risk": 3,
                      "perclos": 0.45,
                      "yawn": 8,
                      "hard_brake": 30,
                      "battery_mv": 2800,
                      "lat": 41.8028,
                      "lon": 123.5497
                    }
                  }]
                }
              }
            }""";

    @BeforeEach
    void setUp() {
        when(vehicleRepository.findByTerminalSn(anyString())).thenReturn(Optional.empty());
        when(tripRepository.findActiveTrips()).thenReturn(Collections.emptyList());
        router = new IotdaAmqpMessageRouter(eventPublisher, vehicleRepository, tripRepository, sensorDataStore);
    }

    @Test
    void route_shouldPublishSensorDataCollected() {
        router.route(NORMAL_JSON);

        ArgumentCaptor<SensorDataCollected> captor = ArgumentCaptor.forClass(SensorDataCollected.class);
        verify(eventPublisher, atLeastOnce()).publish(captor.capture());

        SensorDataCollected event = captor.getValue();
        assertEquals("6a44f1047f2e6c302f80df85_vehicle_safety", event.deviceId());

        List<SensorReading> readings = event.readings();
        assertTrue(readings.size() >= 3);

        boolean hasDmsCamera = readings.stream().anyMatch(r -> r.sensorType() == SensorReading.SensorType.DMS_CAMERA);
        boolean hasAccel = readings.stream().anyMatch(r -> r.sensorType() == SensorReading.SensorType.ACCELEROMETER);
        boolean hasEnv = readings.stream().anyMatch(r -> r.sensorType() == SensorReading.SensorType.ENVIRONMENT);
        boolean hasRadar = readings.stream().anyMatch(r -> r.sensorType() == SensorReading.SensorType.MILLIMETER_WAVE_RADAR);

        assertTrue(hasDmsCamera);
        assertTrue(hasAccel);
        assertTrue(hasEnv);
        assertTrue(hasRadar);
    }

    @Test
    void route_shouldPublishPhysiologicalDataUpdated() {
        router.route(NORMAL_JSON);

        ArgumentCaptor<PhysiologicalDataUpdated> captor = ArgumentCaptor.forClass(PhysiologicalDataUpdated.class);
        verify(eventPublisher, atLeastOnce()).publish(captor.capture());

        PhysiologicalDataUpdated event = captor.getValue();
        PhysiologicalSnapshot snapshot = event.snapshot();
        assertEquals(72, snapshot.heartRate());
        assertEquals(98.5, snapshot.bloodOxygen());
        assertEquals(65, snapshot.restingHr());
    }

    @Test
    void route_shouldPublishVehicleStateUpdated() {
        router.route(NORMAL_JSON);

        ArgumentCaptor<VehicleStateUpdated> captor = ArgumentCaptor.forClass(VehicleStateUpdated.class);
        verify(eventPublisher, atLeastOnce()).publish(captor.capture());

        VehicleStateUpdated event = captor.getValue();
        VehicleStateSnapshot state = event.state();
        assertEquals(41.8028, state.latitude());
        assertEquals(123.5497, state.longitude());
        assertEquals(1, state.gpsFix());
    }

    @Test
    void route_shouldPublishBehaviorCountersUpdated() {
        router.route(NORMAL_JSON);

        ArgumentCaptor<BehaviorCountersUpdated> captor = ArgumentCaptor.forClass(BehaviorCountersUpdated.class);
        verify(eventPublisher, atLeastOnce()).publish(captor.capture());

        BehaviorCountersUpdated event = captor.getValue();
        DrivingBehaviorCounters counters = event.counters();
        assertEquals(24, counters.getSuddenBrakingCount());
        assertEquals(59, counters.getSuddenAccelerationCount());
        assertEquals(32, counters.getSharpTurnCount());
    }

    @Test
    void route_shouldPublishSafetyAlerts_whenRiskIsHigh() {
        router.route(ALERT_JSON);

        ArgumentCaptor<SafetyAlertDetectedEvent> captor = ArgumentCaptor.forClass(SafetyAlertDetectedEvent.class);
        verify(eventPublisher, atLeastOnce()).publish(captor.capture());

        List<SafetyAlertDetectedEvent> alerts = captor.getAllValues();
        assertTrue(alerts.size() >= 4);

        boolean hasSystemRiskL3 = alerts.stream()
                .anyMatch(a -> a.alertType() == AlertType.SYSTEM_RISK && a.riskLevel() == RiskLevel.L3_CRITICAL);
        boolean hasFatigue = alerts.stream()
                .anyMatch(a -> a.alertType() == AlertType.FATIGUE);
        boolean hasDistraction = alerts.stream()
                .anyMatch(a -> a.alertType() == AlertType.DISTRACTION);
        boolean hasLowBattery = alerts.stream()
                .anyMatch(a -> a.alertType() == AlertType.LOW_BATTERY);

        assertTrue(hasSystemRiskL3, "Should have SYSTEM_RISK L3 alert when risk=3");
        assertTrue(hasFatigue, "Should have FATIGUE alert when perclos>0.3");
        assertTrue(hasDistraction, "Should have DISTRACTION alert when yawn>5");
        assertTrue(hasLowBattery, "Should have LOW_BATTERY alert when battery_mv<3300");
    }

    @Test
    void route_shouldPublishSuddenBraking_whenBrakeCountIncreases() {
        router.route(NORMAL_JSON);
        reset(eventPublisher);

        String jsonWithBrakeIncrease = NORMAL_JSON.replaceFirst("\"hard_brake\": 24", "\"hard_brake\": 25");
        router.route(jsonWithBrakeIncrease);

        ArgumentCaptor<SafetyAlertDetectedEvent> captor = ArgumentCaptor.forClass(SafetyAlertDetectedEvent.class);
        verify(eventPublisher, atLeastOnce()).publish(captor.capture());

        boolean hasSuddenBraking = captor.getAllValues().stream()
                .anyMatch(a -> a.alertType() == AlertType.SUDDEN_BRAKING);
        assertTrue(hasSuddenBraking);
    }

    @Test
    void route_shouldNotCreatePhysiologicalSnapshot_whenHrAndSpo2Absent() {
        String json = NORMAL_JSON.replace("\"hr\": 72, \"spo2\": 98.5, \"resting_hr\": 65,", "");

        router.route(json);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher, atLeast(0)).publish(captor.capture());

        boolean hasPhysiological = captor.getAllValues().stream()
                .anyMatch(e -> e instanceof PhysiologicalDataUpdated);
        assertFalse(hasPhysiological);
    }

    @Test
    void route_shouldHandleEmptyServices() {
        String json = """
                {
                  "notify_data": {
                    "header": { "device_id": "test", "node_id": "n", "product_id": "p",
                               "app_id": "a", "gateway_id": "g" },
                    "body": { "services": [] }
                  }
                }""";

        assertDoesNotThrow(() -> router.route(json));
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void route_shouldHandleInvalidJson() {
        assertDoesNotThrow(() -> router.route("not valid json"));
        verify(eventPublisher, never()).publish(any());
    }
}
