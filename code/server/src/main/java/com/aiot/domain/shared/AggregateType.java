package com.aiot.domain.shared;

/**
 * 聚合根类型枚举，穷举系统中五类聚合根。
 * 用于 AggregateId 中提供编译期可区分的聚合根类型标签。
 */
public enum AggregateType {
    TRIP,
    DRIVER,
    VEHICLE,
    SYSTEM_ACCOUNT,
    ROAD_RAGE_VOICE_RECORD
}
