package com.aiot.domain.model;

/**
 * 驾驶员覆盖操作类型（VO-21 配套枚举）
 * 穷举驾驶员主动接管车辆的操作类型，用于判断是否中止干预升级
 */
public enum OverrideType {
    /** 转向操作 */
    TURNING,
    /** 制动操作 */
    BRAKING,
    /** 加速操作 */
    ACCELERATING,
    /** 驾驶员恢复自动驾驶，干预恢复 */
    RESUMING
}