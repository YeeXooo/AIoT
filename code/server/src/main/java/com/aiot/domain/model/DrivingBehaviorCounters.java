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
    private final int heavyFatigueCount;
    private final int distractionCount;
    private final int roadRageCount;
    private final int sharpTurnCount;

    private DrivingBehaviorCounters(int suddenBrakingCount, int suddenAccelerationCount,
                                     int heavyFatigueCount, int distractionCount, int roadRageCount,
                                     int sharpTurnCount) {
        if (suddenBrakingCount < 0 || suddenAccelerationCount < 0
                || heavyFatigueCount < 0 || distractionCount < 0 || roadRageCount < 0
                || sharpTurnCount < 0)
            throw new IllegalArgumentException("计数不能为负数");
        this.suddenBrakingCount = suddenBrakingCount;
        this.suddenAccelerationCount = suddenAccelerationCount;
        this.heavyFatigueCount = heavyFatigueCount;
        this.distractionCount = distractionCount;
        this.roadRageCount = roadRageCount;
        this.sharpTurnCount = sharpTurnCount;
    }

    public static DrivingBehaviorCounters of(int suddenBrakingCount, int suddenAccelerationCount) {
        return new DrivingBehaviorCounters(suddenBrakingCount, suddenAccelerationCount, 0, 0, 0, 0);
    }

    public static DrivingBehaviorCounters of(int suddenBrakingCount, int suddenAccelerationCount,
                                              int heavyFatigueCount, int distractionCount, int roadRageCount) {
        return new DrivingBehaviorCounters(suddenBrakingCount, suddenAccelerationCount,
                heavyFatigueCount, distractionCount, roadRageCount, 0);
    }

    public static DrivingBehaviorCounters of(int suddenBrakingCount, int suddenAccelerationCount,
                                              int heavyFatigueCount, int distractionCount, int roadRageCount,
                                              int sharpTurnCount) {
        return new DrivingBehaviorCounters(suddenBrakingCount, suddenAccelerationCount,
                heavyFatigueCount, distractionCount, roadRageCount, sharpTurnCount);
    }

    public static DrivingBehaviorCounters init() {
        return new DrivingBehaviorCounters(0, 0, 0, 0, 0, 0);
    }

    public DrivingBehaviorCounters incrementBraking() {
        return new DrivingBehaviorCounters(this.suddenBrakingCount + 1, this.suddenAccelerationCount,
                this.heavyFatigueCount, this.distractionCount, this.roadRageCount, this.sharpTurnCount);
    }

    public DrivingBehaviorCounters incrementAcceleration() {
        return new DrivingBehaviorCounters(this.suddenBrakingCount, this.suddenAccelerationCount + 1,
                this.heavyFatigueCount, this.distractionCount, this.roadRageCount, this.sharpTurnCount);
    }

    public DrivingBehaviorCounters incrementHeavyFatigue() {
        return new DrivingBehaviorCounters(this.suddenBrakingCount, this.suddenAccelerationCount,
                this.heavyFatigueCount + 1, this.distractionCount, this.roadRageCount, this.sharpTurnCount);
    }

    public DrivingBehaviorCounters incrementDistraction() {
        return new DrivingBehaviorCounters(this.suddenBrakingCount, this.suddenAccelerationCount,
                this.heavyFatigueCount, this.distractionCount + 1, this.roadRageCount, this.sharpTurnCount);
    }

    public DrivingBehaviorCounters incrementRoadRage() {
        return new DrivingBehaviorCounters(this.suddenBrakingCount, this.suddenAccelerationCount,
                this.heavyFatigueCount, this.distractionCount, this.roadRageCount + 1, this.sharpTurnCount);
    }

    public DrivingBehaviorCounters incrementSharpTurn() {
        return new DrivingBehaviorCounters(this.suddenBrakingCount, this.suddenAccelerationCount,
                this.heavyFatigueCount, this.distractionCount, this.roadRageCount, this.sharpTurnCount + 1);
    }

    protected DrivingBehaviorCounters() {
        this.suddenBrakingCount = 0;
        this.suddenAccelerationCount = 0;
        this.heavyFatigueCount = 0;
        this.distractionCount = 0;
        this.roadRageCount = 0;
        this.sharpTurnCount = 0;
    }
}
