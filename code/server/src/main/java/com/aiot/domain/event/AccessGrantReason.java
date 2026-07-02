package com.aiot.domain.event;
import org.springframework.stereotype.Service;

/**
 * 权限授予原因枚举。
 */
public enum AccessGrantReason {
    /** 常规（L3 持续 >60s） */
    NORMAL,
    /** 高危自动激活（BR-06 触发） */
    EMERGENCY,
    /** 物理遮挡解除恢复 */
    OCCLUSION_RESTORED
}
