package com.aiot.domain.model;

import com.aiot.domain.model.exception.BusinessException;

/**
 * 驾驶员覆盖操作类型（VO-21 配套枚举）
 * 驾驶员主动接管车辆的操作类型
 */
public enum OverrideType {
    /** 转向操作 */
    TURNING,
    /** 制动操作 */
    BRAKING,
    /** 加速操作 */
    ACCELERATING;

    /**
     * 字符串转覆盖操作类型枚举，内置业务校验
     * @param typeCode 操作类型字符串
     * @return 对应枚举值
     * @throws BusinessException 空值/非法值时抛出
     */
    public static OverrideType of(String typeCode) {
        if (typeCode == null || typeCode.isBlank()) {
            throw new BusinessException(
                    "MODEL_015",
                    "覆盖操作类型不能为空",
                    "OVERRIDE_TYPE_VALIDATE"
            );
        }
        try {
            return OverrideType.valueOf(typeCode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    "MODEL_016",
                    String.format("覆盖操作类型非法，当前值：%s，合法值：TURNING、BRAKING、ACCELERATING", typeCode),
                    "OVERRIDE_TYPE_VALIDATE"
            );
        }
    }
}