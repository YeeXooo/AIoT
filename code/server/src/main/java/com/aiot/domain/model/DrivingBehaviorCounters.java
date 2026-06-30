package com.aiot.domain.model;

import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 驾驶行为计数器（VO-16）
 * 单行程累计急刹、急加速次数，用于评分与统计
 */
@Embeddable
@Getter
@EqualsAndHashCode
public final class DrivingBehaviorCounters {
    private final int suddenBrakingCount;
    private final int suddenAccelerationCount;

    private DrivingBehaviorCounters(int suddenBrakingCount, int suddenAccelerationCount) {
        if (suddenBrakingCount < 0 || suddenAccelerationCount < 0)
            throw new IllegalArgumentException("计数不能为负数");
        this.suddenBrakingCount = suddenBrakingCount;
        this.suddenAccelerationCount = suddenAccelerationCount;
    }

    public static DrivingBehaviorCounters of(int suddenBrakingCount, int suddenAccelerationCount) {
        return new DrivingBehaviorCounters(suddenBrakingCount, suddenAccelerationCount);
    }

    public static DrivingBehaviorCounters init() {
        return new DrivingBehaviorCounters(0, 0);
    }

    public DrivingBehaviorCounters incrementBraking() {
        return new DrivingBehaviorCounters(this.suddenBrakingCount + 1, this.suddenAccelerationCount);
    }

    public DrivingBehaviorCounters incrementAcceleration() {
        return new DrivingBehaviorCounters(this.suddenBrakingCount, this.suddenAccelerationCount + 1);
    }

    protected DrivingBehaviorCounters() {
        this.suddenBrakingCount = 0;
        this.suddenAccelerationCount = 0;
    }
}