package com.aiot.domain.event;

/**
 * 权限撤销原因枚举。
 */
public enum AccessRevocationReason {
    /** 风险下降不再持续 */
    RISK_DECREASED,
    /** 物理遮挡 */
    PHYSICAL_OCCLUSION,
    /** 驾驶员注销 */
    DRIVER_DEACTIVATED
}
