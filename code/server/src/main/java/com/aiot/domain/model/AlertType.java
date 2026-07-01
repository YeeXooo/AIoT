package com.aiot.domain.model;

import com.aiot.domain.model.exception.BusinessException;

/**
 * 告警类型（VO-02）
 * 标识告警种类，驱动通知路由与干预策略选择
 */
public enum AlertType {
    /** 疲劳驾驶 */
    FATIGUE,
    /** 分心驾驶 */
    DISTRACTION,
    /** 路怒行为 */
    ROAD_RAGE,
    /** 活体检测 */
    LIFE_DETECTION,
    /** 碰撞失能 */
    COLLISION_DISABILITY,
    /** 性能预警 */
    PERFORMANCE_WARNING;

    /**
     * 字符串转告警类型枚举，内置业务校验
     * @param typeCode 类型字符串
     * @return 对应枚举值
     * @throws BusinessException 空值/非法值时抛出
     */
    public static AlertType of(String typeCode) {
        if (typeCode == null || typeCode.isBlank()) {
            throw new BusinessException(
                    "MODEL_003",
                    "告警类型不能为空",
                    "ALERT_TYPE_VALIDATE"
            );
        }
        try {
            return AlertType.valueOf(typeCode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    "MODEL_004",
                    String.format("告警类型非法，当前值：%s，合法值：FATIGUE、DISTRACTION、ROAD_RAGE、LIFE_DETECTION、COLLISION_DISABILITY、PERFORMANCE_WARNING", typeCode),
                    "ALERT_TYPE_VALIDATE"
            );
        }
    }

}