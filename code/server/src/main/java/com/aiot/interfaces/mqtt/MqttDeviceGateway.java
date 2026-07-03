package com.aiot.interfaces.mqtt;

import static com.aiot.interfaces.mqtt.MqttPayloads.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * MQTT 设备网关（双模式：真实 Paho Client / 日志存根）。
 * <p>
 * 当 {@code aiot.mqtt.enabled=true} 时使用真实 MQTT 连接 IoTDA Broker；
 * 否则回退到日志模拟模式，仅记录消息内容不进行网络通信。
 * </p>
 * <p>
 * 设计依据：docs/ood_interface.md §2、docs/ood_infrastructure.md §3.5.1
 * </p>
 */
@Component
public class MqttDeviceGateway {

    private static final Logger log = LoggerFactory.getLogger(MqttDeviceGateway.class);

    private final MqttProperties properties;
    private final ObjectMapper objectMapper;
    private final MqttClientManager clientManager;

    public MqttDeviceGateway(MqttProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clientManager = new MqttClientManager(properties, objectMapper);
        this.clientManager.start();
    }

    // ── 上行消息（设备 → 云）──

    /** 上行：流式感知数据。 */
    public void publishSensorReading(String deviceId, String sensorType, SensorReadingPayload payload) {
        String topic = MqttTopics.sensorUp(deviceId, sensorType);
        publishOrLog(topic, payload, "上行-感知");
    }

    /** 上行：行程状态变更。 */
    public void publishTripStatus(String deviceId, TripStatusPayload payload) {
        String topic = MqttTopics.tripStatusUp(deviceId);
        publishOrLog(topic, payload, "上行-行程状态");
    }

    /** 上行：告警事件。 */
    public void publishAlert(String deviceId, AlertPayload payload) {
        String topic = MqttTopics.alertUp(deviceId);
        publishOrLog(topic, payload, "上行-告警");
    }

    /** 上行：生理体征快照。 */
    public void publishPhysiologicalSnapshot(String deviceId, PhysiologicalSnapshotPayload payload) {
        String topic = MqttTopics.PHYSIOLOGICAL_SNAPSHOT_UP.replace("{deviceId}", deviceId);
        publishOrLog(topic, payload, "上行-生理");
    }

    /** 上行：车辆状态遥测。 */
    public void publishVehicleState(String deviceId, VehicleStatePayload payload) {
        String topic = MqttTopics.VEHICLE_STATE_UP.replace("{deviceId}", deviceId);
        publishOrLog(topic, payload, "上行-车辆状态");
    }

    /** 上行：设备心跳。 */
    public void publishHeartbeat(String deviceId, HeartbeatPayload payload) {
        String topic = MqttTopics.heartbeatUp(deviceId);
        publishOrLog(topic, payload, "上行-心跳");
    }

    /** 上行：传感器故障。 */
    public void publishSensorFault(String deviceId, SensorFaultPayload payload) {
        String topic = MqttTopics.SENSOR_FAULT_UP.replace("{deviceId}", deviceId);
        publishOrLog(topic, payload, "上行-传感器故障");
    }

    /** 上行：摄像头遮挡。 */
    public void publishSensorOcclusion(String deviceId, CameraOcclusionPayload payload) {
        String topic = MqttTopics.SENSOR_OCCLUSION_UP.replace("{deviceId}", deviceId);
        publishOrLog(topic, payload, "上行-遮挡");
    }

    /** 上行：驾驶员覆盖信号。 */
    public void publishDriverOverride(String deviceId, OverrideSignalPayload payload) {
        String topic = MqttTopics.DRIVER_OVERRIDE_UP.replace("{deviceId}", deviceId);
        publishOrLog(topic, payload, "上行-覆盖");
    }

    /** 上行：行程评分。 */
    public void publishTripScore(String deviceId, TripScorePayload payload) {
        String topic = MqttTopics.TRIP_SCORE_UP.replace("{deviceId}", deviceId);
        publishOrLog(topic, payload, "上行-评分");
    }

    /** 上行：语音存证。 */
    public void publishVoiceEvidence(String deviceId, byte[] payload) {
        if (properties.isEnabled()) {
            String topic = MqttTopics.VOICE_EVIDENCE_UP.replace("{deviceId}", deviceId);
            clientManager.publish(topic, payload, properties.getDefaultQos());
        } else {
            log.info("[MQTT 上行-语音存证] deviceId={}, size={}bytes", deviceId, payload.length);
        }
    }

    /** 上行：指令执行确认。 */
    public void publishCommandAck(String deviceId, String commandId, CommandAckPayload payload) {
        String topic = MqttTopics.COMMAND_ACK.replace("{deviceId}", deviceId).replace("{commandId}", commandId);
        publishOrLog(topic, payload, "上行-指令确认");
    }

    // ── 下行消息（云 → 设备）──

    /** 下行：干预指令。 */
    public void sendInterventionCommand(String deviceId, InterventionCommandPayload payload) {
        String topic = MqttTopics.cmdInterventionDown(deviceId);
        publishOrLogDownlink(topic, payload, "下行-干预");
    }

    /** 下行：车窗控制指令。 */
    public void sendWindowCommand(String deviceId, WindowCommandPayload payload) {
        String topic = MqttTopics.cmdWindowDown(deviceId);
        publishOrLogDownlink(topic, payload, "下行-车窗");
    }

    /** 下行：车门解锁指令。 */
    public void sendDoorUnlockCommand(String deviceId, DoorUnlockPayload payload) {
        String topic = MqttTopics.CMD_DOOR_UNLOCK_DOWN.replace("{deviceId}", deviceId);
        publishOrLogDownlink(topic, payload, "下行-车门");
    }

    /** 下行：OTA 升级包分片。 */
    public void sendOtaPackage(String deviceId, OtaPackagePayload payload) {
        String topic = MqttTopics.cmdOtaDown(deviceId);
        publishOrLogDownlink(topic, payload, "下行-OTA");
    }

    /** 下行：OTA 回滚指令。 */
    public void sendOtaRollback(String deviceId, OtaRollbackPayload payload) {
        String topic = MqttTopics.CMD_OTA_ROLLBACK_DOWN.replace("{deviceId}", deviceId);
        publishOrLogDownlink(topic, payload, "下行-OTA回滚");
    }

    /** 下行：SparkRTC 入房凭证。 */
    public void sendMediaJoin(String deviceId, MediaJoinPayload payload) {
        String topic = MqttTopics.CMD_MEDIA_JOIN_DOWN.replace("{deviceId}", deviceId);
        publishOrLogDownlink(topic, payload, "下行-媒体入房");
    }

    // ── 推送消息（云 → APP/大屏）──

    /** 推送：家属告警。 */
    public void pushFamilyAlert(String accountId, AlertPushPayload payload) {
        String topic = MqttTopics.familyAlertPush(accountId);
        publishOrLogPush(topic, payload, "推送-告警");
    }

    /** 推送：家属状态快照。 */
    public void pushFamilyStatus(String accountId, Object payload) {
        String topic = MqttTopics.familyStatusPush(accountId);
        publishOrLogPush(topic, payload, "推送-状态");
    }

    /** 推送：家属权限授予。 */
    public void pushFamilyAccessGranted(String accountId, AccessGrantedPayload payload) {
        String topic = MqttTopics.FAMILY_ACCESS_GRANTED.replace("{accountId}", accountId);
        publishOrLogPush(topic, payload, "推送-权限授予");
    }

    /** 推送：家属权限撤销。 */
    public void pushFamilyAccessRevoked(String accountId, AccessRevokedPayload payload) {
        String topic = MqttTopics.FAMILY_ACCESS_REVOKED.replace("{accountId}", accountId);
        publishOrLogPush(topic, payload, "推送-权限撤销");
    }

    /** 推送：SOS 确认通知。 */
    public void pushRescueConfirm(String accountId, RescueConfirmPayload payload) {
        String topic = MqttTopics.APP_RESCUE_CONFIRM.replace("{accountId}", accountId);
        publishOrLogPush(topic, payload, "推送-SOS确认");
    }

    /** 推送：车队 L3 告警。 */
    public void pushFleetAlert(String fleetId, FleetAlertPayload payload) {
        String topic = MqttTopics.FLEET_ALERT_PUSH.replace("{fleetId}", fleetId);
        publishOrLogPush(topic, payload, "推送-车队告警");
    }

    /** 推送：绩效预警。 */
    public void pushPerformanceWarning(String fleetId, PerformanceWarningPushPayload payload) {
        String topic = MqttTopics.FLEET_PERF_WARNING_PUSH.replace("{fleetId}", fleetId);
        publishOrLogPush(topic, payload, "推送-绩效预警");
    }

    // ── 下行消息订阅入口（云端注册上行消费者）──

    /**
     * 注册上行消息消费者。
     * <p>
     * 当 MQTT 启用时，通过真实 Broker 订阅设备上行主题；
     * 未启用时，该注册为 no-op（模拟数据由内部数据源直接注入应用服务）。
     * </p>
     */
    public void onSensorReading(String deviceId, java.util.function.Consumer<SensorReadingPayload> consumer) {
        if (properties.isEnabled()) {
            clientManager.registerHandler(
                    MqttTopics.SENSOR_UP.replace("{deviceId}", deviceId).replace("{sensorType}", "+"),
                    (topic, bytes) -> {
                        try {
                            consumer.accept(objectMapper.readValue(bytes, SensorReadingPayload.class));
                        } catch (Exception e) {
                            log.error("解析 SensorReading 失败: topic={}", topic, e);
                        }
                    });
        }
    }

    public void onAlert(String deviceId, java.util.function.Consumer<AlertPayload> consumer) {
        if (properties.isEnabled()) {
            clientManager.registerHandler(
                    MqttTopics.alertUp(deviceId),
                    (topic, bytes) -> {
                        try {
                            consumer.accept(objectMapper.readValue(bytes, AlertPayload.class));
                        } catch (Exception e) {
                            log.error("解析 Alert 失败: topic={}", topic, e);
                        }
                    });
        }
    }

    public void onTripStatus(String deviceId, java.util.function.Consumer<TripStatusPayload> consumer) {
        if (properties.isEnabled()) {
            clientManager.registerHandler(
                    MqttTopics.tripStatusUp(deviceId),
                    (topic, bytes) -> {
                        try {
                            consumer.accept(objectMapper.readValue(bytes, TripStatusPayload.class));
                        } catch (Exception e) {
                            log.error("解析 TripStatus 失败: topic={}", topic, e);
                        }
                    });
        }
    }

    public void onCommandAck(String deviceId, java.util.function.BiConsumer<String, CommandAckPayload> consumer) {
        if (properties.isEnabled()) {
            clientManager.registerHandler(
                    MqttTopics.COMMAND_ACK.replace("{deviceId}", deviceId).replace("{commandId}", "+"),
                    (topic, bytes) -> {
                        try {
                            String[] parts = topic.split("/");
                            String commandId = parts[parts.length - 2];
                            consumer.accept(commandId, objectMapper.readValue(bytes, CommandAckPayload.class));
                        } catch (Exception e) {
                            log.error("解析 CommandAck 失败: topic={}", topic, e);
                        }
                    });
        }
    }

    // ── 内部辅助 ──

    private void publishOrLog(String topic, Object payload, String label) {
        if (properties.isEnabled()) {
            clientManager.publishJson(topic, payload, properties.getDefaultQos());
        } else {
            log.info("[MQTT {}] topic={}, qos={}, payload={}",
                    label, topic, properties.getDefaultQos(), toJson(payload));
        }
    }

    private void publishOrLogDownlink(String topic, Object payload, String label) {
        publishOrLog(topic, payload, label);
    }

    private void publishOrLogPush(String topic, Object payload, String label) {
        publishOrLog(topic, payload, label);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }
}
