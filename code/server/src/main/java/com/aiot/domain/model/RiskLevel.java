package com.aiot.domain.model;

import com.aiot.domain.model.exception.BusinessException;

/**
 * 风险等级（VO-01）
 * 统一的三级风险严重度标签，贯穿全系统判定与干预逻辑
 * L1_HINT 为预留等级，本期无业务触发路径
 */
public enum RiskLevel {
    /** 提示级（预留） */
    L1_HINT,
    /** 警告级 */
    L2_WARNING,
    /** 紧急级 */
    L3_CRITICAL;

    /**
     * 字符串转风险等级枚举，内置业务校验
     * @param levelCode 等级字符串
     * @return 对应枚举值
     * @throws BusinessException 空值/非法值时抛出
     */
    public static RiskLevel of(String levelCode) {
        if (levelCode == null || levelCode.isBlank()) {
            throw new BusinessException(
                    "MODEL_001",
                    "风险等级不能为空",
                    "RISK_LEVEL_VALIDATE"
            );
        }
        try {
            return RiskLevel.valueOf(levelCode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    "MODEL_002",
                    String.format("风险等级非法，当前值：%s，合法值：L1_HINT、L2_WARNING、L3_CRITICAL", levelCode),
                    "RISK_LEVEL_VALIDATE"
            );
        }
    }
}