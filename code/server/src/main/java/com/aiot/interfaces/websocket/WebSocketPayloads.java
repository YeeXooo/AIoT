package com.aiot.interfaces.websocket;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * WebSocket 消息负载 DTO。
 * <p>
 * 与前端 model/websocket.ts 中的类型定义一一对应。
 * </p>
 * <p>
 * 设计依据：docs/ood_interface.md §3、docs/communication_architecture.md §2.5
 * </p>
 */
public final class WebSocketPayloads {

    private WebSocketPayloads() {}

    // ── 下行消息 (云端 → APP/大屏) ──

    /** 连接建立确认 */
    public record ConnectionEstablished(String type, String connectionId, String accountId) {
        public static ConnectionEstablished of(String connectionId, String accountId) {
            return new ConnectionEstablished("connection_established", connectionId, accountId);
        }
    }

    /** 心跳 Ping */
    public record Ping(String type, Instant serverTime) {
        public static Ping now() {
            return new Ping("ping", Instant.now());
        }
    }

    /** 驾驶员状态快照 (≥1Hz) */
    public record DriverStatusSnapshot(
            String type,
            String driverId,
            String vehicleId,
            Instant timestamp,
            @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, String> activeAlertLevels,
            @JsonInclude(JsonInclude.Include.NON_NULL) GeoLocation gpsLocation,
            @JsonInclude(JsonInclude.Include.NON_NULL) Double speed,
            String tripStatus,
            @JsonInclude(JsonInclude.Include.NON_NULL) PhysiologicalDigest physiologicalSummary,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<WindowStatus> windowStatus
    ) {
        public static DriverStatusSnapshot create(String driverId, String vehicleId, String tripStatus) {
            return new DriverStatusSnapshot("driver_status_snapshot",
                    driverId, vehicleId, Instant.now(), null, null, null, tripStatus, null, null);
        }
    }

    /** 家属告警推送 */
    public record AlertTriggered(
            String type,
            String alertId,
            String alertType,
            String riskLevel,
            Instant occurredAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) Instant resolvedAt,
            String tripId,
            @JsonInclude(JsonInclude.Include.NON_NULL) GeoLocation gpsLocation
    ) {
        public static AlertTriggered of(String alertId, String alertType, String riskLevel,
                                         Instant occurredAt, String tripId) {
            return new AlertTriggered("alert_triggered", alertId, alertType,
                    riskLevel, occurredAt, null, tripId, null);
        }
    }

    /** 家属权限授予 */
    public record AccessGranted(
            String type,
            String driverId,
            @JsonInclude(JsonInclude.Include.NON_NULL) String sessionToken,
            @JsonInclude(JsonInclude.Include.NON_NULL) String sparkRTCRoomId,
            @JsonInclude(JsonInclude.Include.NON_NULL) String sparkRTCJoinToken,
            String reason
    ) {
        public static AccessGranted of(String driverId, String reason) {
            return new AccessGranted("access_granted", driverId, null, null, null, reason);
        }
    }

    /** 家属权限撤销 */
    public record AccessRevoked(String type, String driverId, String reason) {
        public static AccessRevoked of(String driverId, String reason) {
            return new AccessRevoked("access_revoked", driverId, reason);
        }
    }

    /** 订阅确认 */
    public record SubscribeStatusAck(
            String type,
            String subscriptionId,
            @JsonInclude(JsonInclude.Include.NON_NULL) DriverStatusSnapshot initialSnapshot
    ) {
        public static SubscribeStatusAck of(String subscriptionId) {
            return new SubscribeStatusAck("subscribe_status_ack", subscriptionId, null);
        }
    }

    /** 救援触发确认 */
    public record RescueTriggered(
            String type,
            String rescueRequestId,
            String rescueReportId,
            String status
    ) {
        public static RescueTriggered of(String rescueRequestId, String rescueReportId, String status) {
            return new RescueTriggered("rescue_triggered", rescueRequestId, rescueReportId, status);
        }
    }

    /** SparkRTC Token 续签 */
    public record TokenRenewed(
            String type,
            String sparkRTCRoomId,
            String sparkRTCJoinToken,
            Instant expiresAt
    ) {
        public static TokenRenewed of(String roomId, String token, Instant expiresAt) {
            return new TokenRenewed("token_renewed", roomId, token, expiresAt);
        }
    }

    /** 错误消息 */
    public record WsError(String type, String code, String message) {
        public static WsError of(String code, String message) {
            return new WsError("error", code, message);
        }
    }

    /** L3 车队告警 */
    public record L3Alert(
            String type,
            String fleetId,
            String driverId,
            String vehicleId,
            String alertType,
            Instant occurredAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) GeoLocation gpsLocation
    ) {
        public static L3Alert create(String fleetId, String driverId, String vehicleId,
                                      String alertType, Instant occurredAt) {
            return new L3Alert("l3_alert", fleetId, driverId, vehicleId, alertType, occurredAt, null);
        }
    }

    /** 绩效预警 */
    public record PerformanceWarning(
            String type,
            String driverId,
            String driverName,
            int score,
            String scorePeriod,
            List<String> primaryPenaltyItems,
            Instant occurredAt
    ) {
        public static PerformanceWarning create(String driverId, String driverName,
                                                  int score, String scorePeriod,
                                                  List<String> penaltyItems) {
            return new PerformanceWarning("performance_warning", driverId, driverName,
                    score, scorePeriod, penaltyItems, Instant.now());
        }
    }

    // ── 辅助类型 ──

    public record GeoLocation(double latitude, double longitude) {}

    public record PhysiologicalDigest(
            @JsonInclude(JsonInclude.Include.NON_NULL) Double heartRate,
            @JsonInclude(JsonInclude.Include.NON_NULL) Double spo2,
            @JsonInclude(JsonInclude.Include.NON_NULL) Double emotionIndex
    ) {}

    public record WindowStatus(
            String windowPosition,
            String state,
            @JsonInclude(JsonInclude.Include.NON_NULL) String lastOperation,
            @JsonInclude(JsonInclude.Include.NON_NULL) String lastOperationResult,
            @JsonInclude(JsonInclude.Include.NON_NULL) Instant updatedAt
    ) {}
}
