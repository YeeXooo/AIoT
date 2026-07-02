package com.aiot.interfaces.mqtt;

/**
 * MQTT 主题常量。
 * <p>
 * 定义设备-云通信的全部 Topic 模板。
 * 主题中 {deviceId} 替换为车载终端序列号，{accountId} 替换为家属/管理员账户标识。
 * </p>
 * <p>
 * 设计依据：docs/ood_interface.md §2.1
 * </p>
 */
public final class MqttTopics {

    private MqttTopics() {}

    // ── 上行 Topic (设备 → 云) ──

    /** 流式感知数据上报 */ public static final String SENSOR_UP = "{deviceId}/sensor/{sensorType}/up";
    /** 行程状态变更 */   public static final String TRIP_STATUS_UP = "{deviceId}/trip/status/up";
    /** 告警事件上报 */   public static final String ALERT_UP = "{deviceId}/alert/up";
    /** 生理体征快照 */   public static final String PHYSIOLOGICAL_SNAPSHOT_UP = "{deviceId}/physiological/snapshot/up";
    /** 车辆状态遥测 */   public static final String VEHICLE_STATE_UP = "{deviceId}/vehicle/state/up";
    /** 设备心跳 */       public static final String HEARTBEAT_UP = "{deviceId}/status/heartbeat/up";
    /** 传感器故障 */     public static final String SENSOR_FAULT_UP = "{deviceId}/sensor/fault/up";
    /** 摄像头遮挡 */     public static final String SENSOR_OCCLUSION_UP = "{deviceId}/sensor/occlusion/up";
    /** 驾驶员覆盖信号 */ public static final String DRIVER_OVERRIDE_UP = "{deviceId}/driver/override/up";
    /** 行程评分上报 */   public static final String TRIP_SCORE_UP = "{deviceId}/trip/score/up";
    /** 语音存证上传 */   public static final String VOICE_EVIDENCE_UP = "{deviceId}/voice/evidence/up";
    /** 指令执行确认 */   public static final String COMMAND_ACK = "{deviceId}/cmd/{commandId}/ack";

    // ── 下行 Topic (云 → 设备) ──

    /** 干预指令下发 */   public static final String CMD_INTERVENTION_DOWN = "{deviceId}/cmd/intervention/down";
    /** 车窗控制指令 */   public static final String CMD_WINDOW_DOWN = "{deviceId}/cmd/window/down";
    /** 车门解锁指令 */   public static final String CMD_DOOR_UNLOCK_DOWN = "{deviceId}/cmd/door/unlock/down";
    /** OTA 升级包 */     public static final String CMD_OTA_DOWN = "{deviceId}/cmd/ota/down";
    /** OTA 回滚指令 */   public static final String CMD_OTA_ROLLBACK_DOWN = "{deviceId}/cmd/ota/rollback/down";
    /** SparkRTC 入房凭证 */ public static final String CMD_MEDIA_JOIN_DOWN = "{deviceId}/cmd/media/join/down";

    // ── 推送 Topic (云 → APP/大屏) ──

    /** 家属告警推送 */       public static final String FAMILY_ALERT_PUSH = "family/{accountId}/alert/push";
    /** 家属状态快照推送 */   public static final String FAMILY_STATUS_PUSH = "family/{accountId}/status/push";
    /** 家属权限授予推送 */   public static final String FAMILY_ACCESS_GRANTED = "family/{accountId}/access/granted";
    /** 家属权限撤销推送 */   public static final String FAMILY_ACCESS_REVOKED = "family/{accountId}/access/revoked";
    /** 车队告警推送 */       public static final String FLEET_ALERT_PUSH = "fleet/{fleetId}/alert/push";
    /** 绩效预警推送 */       public static final String FLEET_PERF_WARNING_PUSH = "fleet/{fleetId}/performance-warning/push";
    /** SOS 确认通知 */       public static final String APP_RESCUE_CONFIRM = "app/{accountId}/rescue/confirm";

    // ── 辅助方法 ──

    public static String sensorUp(String deviceId, String sensorType) {
        return SENSOR_UP.replace("{deviceId}", deviceId).replace("{sensorType}", sensorType);
    }

    public static String tripStatusUp(String deviceId) {
        return TRIP_STATUS_UP.replace("{deviceId}", deviceId);
    }

    public static String alertUp(String deviceId) {
        return ALERT_UP.replace("{deviceId}", deviceId);
    }

    public static String heartbeatUp(String deviceId) {
        return HEARTBEAT_UP.replace("{deviceId}", deviceId);
    }

    public static String cmdInterventionDown(String deviceId) {
        return CMD_INTERVENTION_DOWN.replace("{deviceId}", deviceId);
    }

    public static String cmdWindowDown(String deviceId) {
        return CMD_WINDOW_DOWN.replace("{deviceId}", deviceId);
    }

    public static String cmdOtaDown(String deviceId) {
        return CMD_OTA_DOWN.replace("{deviceId}", deviceId);
    }

    public static String familyAlertPush(String accountId) {
        return FAMILY_ALERT_PUSH.replace("{accountId}", accountId);
    }

    public static String familyStatusPush(String accountId) {
        return FAMILY_STATUS_PUSH.replace("{accountId}", accountId);
    }
}
