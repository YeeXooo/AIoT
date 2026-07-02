package com.aiot.domain.model;

import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 驾驶员综合风险评分（VO-22）
 * 跨行程加权平均的整体风险水平，独立于单行程 TripScore
 */
@Embeddable
@Getter
@EqualsAndHashCode
public final class DriverComprehensiveScore {
    private final int value;

    private DriverComprehensiveScore(int value) {
        if (value < 0 || value > 100) { throw new IllegalArgumentException("评分必须在 0~100 范围内"); }
        this.value = value;
    }

    public static DriverComprehensiveScore of(int value) {
        return new DriverComprehensiveScore(value);
    }

    protected DriverComprehensiveScore() {
        this.value = 0;
    }
}