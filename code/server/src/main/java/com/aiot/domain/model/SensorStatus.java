package com.aiot.domain.model;

import com.aiot.domain.model.exception.BusinessException;

/**
 * 传感器状态（VO-06）
 * 描述单个传感器/设备通道的健康状态
 */
public enum SensorStatus {
    /** 在线正常 */
    ONLINE,
    /** 离线失联 */
    OFFLINE,
    /** 故障异常 */
    FAULT;

    /**
     * 字符串转传感器状态枚举，内置业务校验
     * @param statusCode 状态字符串
     * @return 对应枚举值
     * @throws BusinessException 空值/非法值时抛出
     */
    public static SensorStatus of(String statusCode) {
        if (statusCode == null || statusCode.isBlank()) {
            throw new BusinessException(
                    "MODEL_005",
                    "传感器状态不能为空",
                    "SENSOR_STATUS_VALIDATE"
            );
        }
        try {
            return SensorStatus.valueOf(statusCode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    "MODEL_006",
                    String.format("传感器状态非法，当前值：%s，合法值：ONLINE、OFFLINE、FAULT", statusCode),
                    "SENSOR_STATUS_VALIDATE"
            );
        }
    }
}