package com.aiot.domain.model;

/**
 * 传感器状态（VO-06）
 * 描述单个传感器/设备通道的健康状态，供失效保护逻辑使用
 */
public enum SensorStatus {
    /** 在线正常 */
    ONLINE,
    /** 离线失联 */
    OFFLINE,
    /** 故障异常 */
    FAULT
}