package com.aiot.domain.event;
import org.springframework.stereotype.Service;

/**
 * 告警类型枚举。
 */
public enum AlertType {
    /** 疲劳 */
    FATIGUE,
    /** 分心 */
    DISTRACTION,
    /** 路怒 */
    ROAD_RAGE,
    /** 活体遗留 */
    LIFE_DETECTION,
    /** 碰撞失能 */
    COLLISION_DISABILITY,
    /** 绩效预警 */
    PERFORMANCE_WARNING,
    /** 急刹事件 */
    SUDDEN_BRAKING,
    /** 电池低电压告警 */
    LOW_BATTERY,
    /** IoTDA 上报的系统风险等级告警 */
    SYSTEM_RISK
}
