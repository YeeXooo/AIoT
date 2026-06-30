package com.aiot.domain.model;

/**
 * 告警类型（VO-02）
 * 标识告警种类，驱动通知路由与干预策略选择
 *
 * 取值边界：各判定事件携带互不重叠的子集
 * - RiskDeterminedEvent: FATIGUE / DISTRACTION / ROAD_RAGE
 * - LifeDetectedEvent: LIFE_DETECTION
 * - EmergencyActivatedEvent: COLLISION_DISABILITY
 * - PerformanceWarningEvent: PERFORMANCE_WARNING
 */
public enum AlertType {
    /** 疲劳驾驶告警 */
    FATIGUE,
    /** 分心驾驶告警 */
    DISTRACTION,
    /** 路怒行为告警 */
    ROAD_RAGE,
    /** 后排活体检测告警 */
    LIFE_DETECTION,
    /** 碰撞失能告警 */
    COLLISION_DISABILITY,
    /** 驾驶性能预警（离线评分触发） */
    PERFORMANCE_WARNING
}