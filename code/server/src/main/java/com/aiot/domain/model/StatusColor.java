package com.aiot.domain.model;

import com.aiot.domain.model.exception.BusinessException;

/**
 * 驾驶员状态色（VO-15 配套枚举）
 * 家属端常态同步的三色状态标识
 */
public enum StatusColor {
    /** 绿色：安全平稳 */
    GREEN,
    /** 黄色：中度风险 */
    YELLOW,
    /** 红色：高危紧急 */
    RED;

    /**
     * 字符串转状态色枚举，内置业务校验
     * @param colorCode 颜色字符串
     * @return 对应枚举值
     * @throws BusinessException 空值/非法值时抛出
     */
    public static StatusColor of(String colorCode) {
        if (colorCode == null || colorCode.isBlank()) {
            throw new BusinessException(
                    "MODEL_011",
                    "状态色不能为空",
                    "STATUS_COLOR_VALIDATE"
            );
        }
        try {
            return StatusColor.valueOf(colorCode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    "MODEL_012",
                    String.format("状态色非法，当前值：%s，合法值：GREEN、YELLOW、RED", colorCode),
                    "STATUS_COLOR_VALIDATE"
            );
        }
    }
}