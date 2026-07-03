package com.aiot.domain.model;
import org.springframework.stereotype.Service;

/**
 * OTA升级阶段（VO-19 配套枚举）
 * 穷举固件升级会话的全生命周期状态，构成升级状态机
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
    ROLLED_BACK
}