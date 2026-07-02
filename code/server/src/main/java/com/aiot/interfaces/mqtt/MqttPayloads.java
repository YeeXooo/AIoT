package com.aiot.interfaces.mqtt;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * MQTT 消息负载 DTO。
 * <p>
 * 所有 DTO 均为不可变 record，使用 {@link JsonInclude.Include#NON_NULL} 序列化。
 * </p>
 * <p>
 * 设计依据：docs/ood_interface.md §2.2
 * </p>
 */
public final class MqttPayloads {

    private MqttPayloads() {}

    // ── 上行 Payload ──

    /** 流式感知数据 (SensorReading) */
    public record SensorReadingPayload(
            Instant timestamp,
            String sensorId,
            String sensorType,
            Map<String, Double> values
    ) {}

    /** 行程状态变更 (TripStatusEvent) */
    public record TripStatusPayload(
            String tripId,
            String driverId,
            String vehicleId,
            String eventType,
            Instant occurredAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) GeoLocationPayload gpsLocation
    ) {}

    /** 告警事件上报 (SafetyAlertEvent) */
    public record AlertPayload(
            String alertId,
            String alertType,
            String riskLevel,
            Instant occurredAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) Instant resolvedAt,
            String tripId,
            @JsonInclude(JsonInclude.Include.NON_NULL) GeoLocationPayload gpsLocation,
            @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> featureSnapshot
    ) {}

    /** 生理体征快照 (PhysiologicalSnapshot) */
    public record PhysiologicalSnapshotPayload(
            String driverId,
            Instant timestamp,
            Double heartRate,
            Double spo2,
            @JsonInclude(JsonInclude.Include.NON_NULL) Double emotionIndex,
            @JsonInclude(JsonInclude.Include.NON_NULL) Double hrResting,
            @JsonInclude(JsonInclude.Include.NON_NULL) Double rrInterval
    ) {}

    /** 车辆状态遥测 (VehicleStateSnapshot) */
    public record VehicleStatePayload(
            String vehicleId,
            Instant timestamp,
            Double speed,
            String doorLockState,
            @JsonInclude(JsonInclude.Include.NON_NULL) AccelerationPayload acceleration,
            @JsonInclude(JsonInclude.Include.NON_NULL) Double fuelLevel,
            @JsonInclude(JsonInclude.Include.NON_NULL) Double odometer,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<WindowStatusEntry> windowStatuses
    ) {}

    /** 加速度数据 */
    public record AccelerationPayload(double x, double y, double z) {}

    /** 车窗状态条目 */
    public record WindowStatusEntry(
            String windowPosition,
            String state,
            @JsonInclude(JsonInclude.Include.NON_NULL) String lastOperation,
            @JsonInclude(JsonInclude.Include.NON_NULL) String lastOperationResult,
            @JsonInclude(JsonInclude.Include.NON_NULL) Instant updatedAt
    ) {}

    /** 设备心跳 (Heartbeat) */
    public record HeartbeatPayload(
            String deviceId,
            Instant timestamp,
            long sequenceNumber,
            @JsonInclude(JsonInclude.Include.NON_NULL) SensorSelfCheckPayload sensorSelfCheck,
            @JsonInclude(JsonInclude.Include.NON_NULL) SystemMetricsPayload systemMetrics
    ) {}

    /** 传感器自检摘要 */
    public record SensorSelfCheckPayload(
            String overallStatus,
            int failedSensorCount,
            Instant lastSelfCheckAt
    ) {}

    /** 系统指标 */
    public record SystemMetricsPayload(
            Double cpuUsage,
            Double memoryUsage,
            Long storageAvailable,
            Long uptimeSeconds
    ) {}

    /** 传感器故障事件 (SensorFailureEvent) */
    public record SensorFaultPayload(
            String deviceId,
            List<FailedSensor> failedSensors,
            Instant occurredAt
    ) {}

    /** 故障传感器 */
    public record FailedSensor(
            String sensorId,
            String sensorType,
            String failureType,
            String errorCode
    ) {}

    /** 摄像头遮挡事件 */
    public record CameraOcclusionPayload(
            String deviceId,
            String sensorId,
            String eventType,
            @JsonInclude(JsonInclude.Include.NON_NULL) String occlusionType,
            Instant occurredAt
    ) {}

    /** 驾驶员覆盖信号 (OverrideSignal) */
    public record OverrideSignalPayload(
            String driverId,
            String tripId,
            String overrideType,
            Instant occurredAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) GeoLocationPayload gpsLocation
    ) {}

    /** 行程评分上报 (TripScore) */
    public record TripScorePayload(
            String tripId,
            String driverId,
            int score,
            List<PenaltyItem> penaltyItems,
            Instant occurredAt
    ) {}

    /** 扣分项 */
    public record PenaltyItem(
            String category,
            double penaltyScore,
            int violationCount,
            String description
    ) {}

    /** 指令执行确认 (CommandAck) */
    public record CommandAckPayload(
            String commandId,
            String result,
            @JsonInclude(JsonInclude.Include.NON_NULL) String failureReason,
            Instant completedAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> detail
    ) {}

    // ── 下行 Payload ──

    /** 干预指令下发 (InterventionInstruction) */
    public record InterventionCommandPayload(
            String commandId,
            Instant issuedAt,
            List<InterventionItem> interventions
    ) {}

    /** 单条干预指令 */
    public record InterventionItem(
            String interventionId,
            String interventionType,
            String targetDevice,
            @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> parameters,
            int priority
    ) {}

    /** 车窗控制指令 */
    public record WindowCommandPayload(
            String commandId,
            String driverId,
            String windowOperation,
            String windowPosition,
            Instant issuedAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) String secondaryAuthToken
    ) {}

    /** 车门解锁指令 */
    public record DoorUnlockPayload(
            String commandId,
            String rescueTokenId,
            String targetVehicleId,
            Instant issuedAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) String rescueTokenSignature
    ) {}

    /** OTA 升级包分片 */
    public record OtaPackagePayload(
            String commandId,
            String taskId,
            OtaVersionPayload newVersion,
            int chunkIndex,
            int totalChunks,
            @JsonInclude(JsonInclude.Include.NON_NULL) Long chunkOffset,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer chunkSize,
            String payload,
            @JsonInclude(JsonInclude.Include.NON_NULL) String checksum,
            Instant issuedAt
    ) {}

    /** OTA 版本号 */
    public record OtaVersionPayload(int major, int minor, int patch, String buildNumber) {}

    /** OTA 回滚指令 */
    public record OtaRollbackPayload(
            String commandId,
            String vehicleId,
            OtaVersionPayload targetVersion,
            String reason,
            Instant issuedAt
    ) {}

    /** SparkRTC 入房凭证 */
    public record MediaJoinPayload(
            String commandId,
            String sparkRTCRoomId,
            String sparkRTCJoinToken,
            Instant issuedAt
    ) {}

    // ── 推送 Payload (云 → APP/大屏) ──

    /** 家属告警推送 */
    public record AlertPushPayload(
            String alertId,
            String alertType,
            String riskLevel,
            String driverId,
            String vehicleId,
            Instant occurredAt,
            String tripId,
            @JsonInclude(JsonInclude.Include.NON_NULL) GeoLocationPayload gpsLocation
    ) {}

    /** 家属权限授予 */
    public record AccessGrantedPayload(
            String driverId,
            @JsonInclude(JsonInclude.Include.NON_NULL) String sessionToken,
            @JsonInclude(JsonInclude.Include.NON_NULL) String sparkRTCRoomId,
            @JsonInclude(JsonInclude.Include.NON_NULL) String sparkRTCJoinToken,
            String reason
    ) {}

    /** 家属权限撤销 */
    public record AccessRevokedPayload(
            String driverId,
            String reason
    ) {}

    /** 车队 L3 告警推送 */
    public record FleetAlertPayload(
            String fleetId,
            String driverId,
            String vehicleId,
            String alertType,
            String riskLevel,
            Instant occurredAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) GeoLocationPayload gpsLocation
    ) {}

    /** 绩效预警推送 */
    public record PerformanceWarningPushPayload(
            String driverId,
            String driverName,
            String fleetId,
            int score,
            String scorePeriod,
            List<String> primaryPenaltyItems,
            Instant occurredAt
    ) {}

    /** SOS 确认通知 */
    public record RescueConfirmPayload(
            String rescueReportId,
            String status,
            @JsonInclude(JsonInclude.Include.NON_NULL) Instant confirmedAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) String message
    ) {}

    /** 地理位置 */
    public record GeoLocationPayload(double latitude, double longitude) {}
}
