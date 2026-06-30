package com.aiot.domain.model;

/**
 * 驾驶员状态色（VO-15 配套枚举）
 * 用于家属端常态状态同步，对应风险等级映射：
 * 无风险/L1 → GREEN，L2 → YELLOW，L3 → RED
 */
public enum StatusColor {
    /** 绿色：安全平稳 */
    GREEN,
    /** 黄色：中度风险 */
    YELLOW,
    /** 红色：高危紧急 */
    RED
}