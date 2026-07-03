package com.aiot.interfaces.mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MqttDeviceGatewayTest {

    @Mock
    private MqttProperties properties;

    @Mock
    private ObjectMapper objectMapper;

    private MqttDeviceGateway gateway;

    @BeforeEach
    void setUp() throws JsonProcessingException {
        lenient().when(properties.isEnabled()).thenReturn(false);
        lenient().when(properties.getDefaultQos()).thenReturn(1);
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        gateway = new MqttDeviceGateway(properties, objectMapper);
    }

    @Test
    void publishSensorReading_shouldNotThrowWhenDisabled() {
        MqttPayloads.SensorReadingPayload payload = new MqttPayloads.SensorReadingPayload(
                Instant.now(), "sensor-1", "camera", Map.of("value", 1.0));

        assertDoesNotThrow(() -> gateway.publishSensorReading("dev-1", "camera", payload));
    }

    @Test
    void publishTripStatus_shouldNotThrowWhenDisabled() {
        MqttPayloads.TripStatusPayload payload = new MqttPayloads.TripStatusPayload(
                "trip-1", "drv-1", "veh-1", "STARTED", Instant.now(), null);

        assertDoesNotThrow(() -> gateway.publishTripStatus("dev-1", payload));
    }

    @Test
    void publishAlert_shouldNotThrowWhenDisabled() {
        MqttPayloads.AlertPayload payload = new MqttPayloads.AlertPayload(
                "alert-1", "COLLISION", "CRITICAL", Instant.now(), null, "trip-1", null, null);

        assertDoesNotThrow(() -> gateway.publishAlert("dev-1", payload));
    }

    @Test
    void publishPhysiologicalSnapshot_shouldNotThrowWhenDisabled() {
        MqttPayloads.PhysiologicalSnapshotPayload payload =
                new MqttPayloads.PhysiologicalSnapshotPayload(
                        "drv-1", Instant.now(), 72.0, 98.0, null, null, null);

        assertDoesNotThrow(() -> gateway.publishPhysiologicalSnapshot("dev-1", payload));
    }

    @Test
    void publishVehicleState_shouldNotThrowWhenDisabled() {
        MqttPayloads.VehicleStatePayload payload = new MqttPayloads.VehicleStatePayload(
                "veh-1", Instant.now(), 60.0, "LOCKED", null, null, null, null);

        assertDoesNotThrow(() -> gateway.publishVehicleState("dev-1", payload));
    }

    @Test
    void publishHeartbeat_shouldNotThrowWhenDisabled() {
        MqttPayloads.HeartbeatPayload payload = new MqttPayloads.HeartbeatPayload(
                "dev-1", Instant.now(), 100L, null, null);

        assertDoesNotThrow(() -> gateway.publishHeartbeat("dev-1", payload));
    }

    @Test
    void publishSensorFault_shouldNotThrowWhenDisabled() {
        MqttPayloads.SensorFaultPayload payload = new MqttPayloads.SensorFaultPayload(
                "dev-1", List.of(), Instant.now());

        assertDoesNotThrow(() -> gateway.publishSensorFault("dev-1", payload));
    }

    @Test
    void publishSensorOcclusion_shouldNotThrowWhenDisabled() {
        MqttPayloads.CameraOcclusionPayload payload = new MqttPayloads.CameraOcclusionPayload(
                "dev-1", "cam-1", "DETECTED", "PHYSICAL_COVER", Instant.now());

        assertDoesNotThrow(() -> gateway.publishSensorOcclusion("dev-1", payload));
    }

    @Test
    void publishDriverOverride_shouldNotThrowWhenDisabled() {
        MqttPayloads.OverrideSignalPayload payload = new MqttPayloads.OverrideSignalPayload(
                "drv-1", "trip-1", "STEERING", Instant.now(), null);

        assertDoesNotThrow(() -> gateway.publishDriverOverride("dev-1", payload));
    }

    @Test
    void publishTripScore_shouldNotThrowWhenDisabled() {
        MqttPayloads.TripScorePayload payload = new MqttPayloads.TripScorePayload(
                "trip-1", "drv-1", 85, List.of(), Instant.now());

        assertDoesNotThrow(() -> gateway.publishTripScore("dev-1", payload));
    }

    @Test
    void publishVoiceEvidence_shouldNotThrowWhenDisabled() {
        assertDoesNotThrow(() -> gateway.publishVoiceEvidence("dev-1", new byte[]{1, 2, 3}));
    }

    @Test
    void publishCommandAck_shouldNotThrowWhenDisabled() {
        MqttPayloads.CommandAckPayload payload = new MqttPayloads.CommandAckPayload(
                "cmd-1", "SUCCESS", null, Instant.now(), null);

        assertDoesNotThrow(() -> gateway.publishCommandAck("dev-1", "cmd-1", payload));
    }

    @Test
    void sendInterventionCommand_shouldNotThrowWhenDisabled() {
        MqttPayloads.InterventionCommandPayload payload =
                new MqttPayloads.InterventionCommandPayload("cmd-1", Instant.now(), List.of());

        assertDoesNotThrow(() -> gateway.sendInterventionCommand("dev-1", payload));
    }

    @Test
    void sendWindowCommand_shouldNotThrowWhenDisabled() {
        MqttPayloads.WindowCommandPayload payload = new MqttPayloads.WindowCommandPayload(
                "cmd-1", "drv-1", "CLOSE", "FRONT_LEFT", Instant.now(), null);

        assertDoesNotThrow(() -> gateway.sendWindowCommand("dev-1", payload));
    }

    @Test
    void sendDoorUnlockCommand_shouldNotThrowWhenDisabled() {
        MqttPayloads.DoorUnlockPayload payload = new MqttPayloads.DoorUnlockPayload(
                "cmd-1", "token-1", "veh-1", Instant.now(), null);

        assertDoesNotThrow(() -> gateway.sendDoorUnlockCommand("dev-1", payload));
    }

    @Test
    void sendOtaPackage_shouldNotThrowWhenDisabled() {
        MqttPayloads.OtaPackagePayload payload = new MqttPayloads.OtaPackagePayload(
                "cmd-1", "task-1",
                new MqttPayloads.OtaVersionPayload(2, 0, 0, "build1"),
                0, 1, null, null, "data", null, Instant.now());

        assertDoesNotThrow(() -> gateway.sendOtaPackage("dev-1", payload));
    }

    @Test
    void sendOtaRollback_shouldNotThrowWhenDisabled() {
        MqttPayloads.OtaRollbackPayload payload = new MqttPayloads.OtaRollbackPayload(
                "cmd-1", "veh-1",
                new MqttPayloads.OtaVersionPayload(1, 0, 0, "old"),
                "bug", Instant.now());

        assertDoesNotThrow(() -> gateway.sendOtaRollback("dev-1", payload));
    }

    @Test
    void sendMediaJoin_shouldNotThrowWhenDisabled() {
        MqttPayloads.MediaJoinPayload payload = new MqttPayloads.MediaJoinPayload(
                "cmd-1", "room-1", "token-abc", Instant.now());

        assertDoesNotThrow(() -> gateway.sendMediaJoin("dev-1", payload));
    }

    @Test
    void pushFamilyAlert_shouldNotThrowWhenDisabled() {
        MqttPayloads.AlertPushPayload payload = new MqttPayloads.AlertPushPayload(
                "alert-1", "COLLISION", "CRITICAL", "drv-1", "veh-1",
                Instant.now(), "trip-1", null);

        assertDoesNotThrow(() -> gateway.pushFamilyAlert("acc-1", payload));
    }

    @Test
    void pushFamilyStatus_shouldNotThrowWhenDisabled() {
        assertDoesNotThrow(() -> gateway.pushFamilyStatus("acc-1", "status-json"));
    }

    @Test
    void pushFamilyAccessGranted_shouldNotThrowWhenDisabled() {
        MqttPayloads.AccessGrantedPayload payload = new MqttPayloads.AccessGrantedPayload(
                "drv-1", "token-1", "room-1", "join-token", "authorized");

        assertDoesNotThrow(() -> gateway.pushFamilyAccessGranted("acc-1", payload));
    }

    @Test
    void pushFamilyAccessRevoked_shouldNotThrowWhenDisabled() {
        MqttPayloads.AccessRevokedPayload payload = new MqttPayloads.AccessRevokedPayload(
                "drv-1", "session expired");

        assertDoesNotThrow(() -> gateway.pushFamilyAccessRevoked("acc-1", payload));
    }

    @Test
    void pushRescueConfirm_shouldNotThrowWhenDisabled() {
        MqttPayloads.RescueConfirmPayload payload = new MqttPayloads.RescueConfirmPayload(
                "report-1", "CONFIRMED", Instant.now(), "Acknowledged");

        assertDoesNotThrow(() -> gateway.pushRescueConfirm("acc-1", payload));
    }

    @Test
    void pushFleetAlert_shouldNotThrowWhenDisabled() {
        MqttPayloads.FleetAlertPayload payload = new MqttPayloads.FleetAlertPayload(
                "fleet-1", "drv-1", "veh-1", "L3_EXCEEDED", "WARNING", Instant.now(), null);

        assertDoesNotThrow(() -> gateway.pushFleetAlert("fleet-1", payload));
    }

    @Test
    void pushPerformanceWarning_shouldNotThrowWhenDisabled() {
        MqttPayloads.PerformanceWarningPushPayload payload =
                new MqttPayloads.PerformanceWarningPushPayload(
                        "drv-1", "John", "fleet-1", 55, "MONTHLY", List.of(), Instant.now());

        assertDoesNotThrow(() -> gateway.pushPerformanceWarning("fleet-1", payload));
    }

    @Test
    void onSensorReading_shouldNotThrowWhenDisabled() {
        assertDoesNotThrow(() -> gateway.onSensorReading("dev-1", p -> {}));
    }

    @Test
    void onAlert_shouldNotThrowWhenDisabled() {
        assertDoesNotThrow(() -> gateway.onAlert("dev-1", p -> {}));
    }

    @Test
    void onTripStatus_shouldNotThrowWhenDisabled() {
        assertDoesNotThrow(() -> gateway.onTripStatus("dev-1", p -> {}));
    }

    @Test
    void onCommandAck_shouldNotThrowWhenDisabled() {
        assertDoesNotThrow(() -> gateway.onCommandAck("dev-1", (id, p) -> {}));
    }
}
