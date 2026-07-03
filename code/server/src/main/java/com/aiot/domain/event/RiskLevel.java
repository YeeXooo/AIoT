package com.aiot.domain.event;
import org.springframework.stereotype.Service;

/**
 * 风险等级枚举。
 * L1 为预留等级，本期无触发路径。
 */
public enum RiskLevel {
    /** 提示级（预留） */
    L1_HINT,
    /** 预警级 */
    L2_WARNING,
    /** 高危级 */
    L3_CRITICAL
}
