package com.aiot.domain.model;

import com.aiot.domain.model.exception.BusinessException;

/**
 * OTA升级阶段（VO-19 配套枚举）
 * 固件升级会话全生命周期状态
 */
public enum UpgradeStage {
    /** 待开始 */
    PENDING,
    /** 传输中 */
    TRANSMITTING,
    /** 校验中 */
    VERIFYING,
    /** 待升级 */
    READY,
    /** 升级中 */
    UPGRADING,
    /** 已完成 */
    COMPLETED,
    /** 回滚中 */
    ROLLING_BACK,
    /** 已回滚 */
    ROLLED_BACK;

    /**
     * 字符串转升级阶段枚举，内置业务校验
     * @param stageCode 阶段字符串
     * @return 对应枚举值
     * @throws BusinessException 空值/非法值时抛出
     */
    public static UpgradeStage of(String stageCode) {
        if (stageCode == null || stageCode.isBlank()) {
            throw new BusinessException(
                    "MODEL_013",
                    "升级阶段不能为空",
                    "UPGRADE_STAGE_VALIDATE"
            );
        }
        try {
            return UpgradeStage.valueOf(stageCode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    "MODEL_014",
                    String.format("升级阶段非法，当前值：%s", stageCode),
                    "UPGRADE_STAGE_VALIDATE"
            );
        }
    }
}