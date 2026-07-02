package com.aiot.domain.model;

import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * L3 持续时长追踪器（VO-17）
 * 追踪 L3 风险累计时长，用于 60 秒授权触发规则
 */
@Embeddable
@Getter
@EqualsAndHashCode
public final class L3DurationTracker {
    private final Instant startTime;
    private final Duration accumulatedDuration;
    private final boolean active;

    private L3DurationTracker(Instant startTime, Duration accumulatedDuration, boolean active) {
        if (startTime == null) { throw new IllegalArgumentException("起始时间不能为空"); }
        if (accumulatedDuration == null || accumulatedDuration.isNegative())
            throw new IllegalArgumentException("累计时长不合法");
        this.startTime = startTime;
        this.accumulatedDuration = accumulatedDuration;
        this.active = active;
    }

    public static L3DurationTracker start(Instant startTime) {
        return new L3DurationTracker(startTime, Duration.ZERO, true);
    }

    public L3DurationTracker advance(Instant now) {
        if (!active) { return this; }
        Duration delta = Duration.between(startTime.plus(accumulatedDuration), now);
        if (delta.isNegative()) { delta = Duration.ZERO; }
        return new L3DurationTracker(startTime, accumulatedDuration.plus(delta), true);
    }

    public L3DurationTracker stop() {
        return new L3DurationTracker(startTime, accumulatedDuration, false);
    }

    protected L3DurationTracker() {
        this.startTime = Instant.EPOCH;
        this.accumulatedDuration = Duration.ZERO;
        this.active = false;
    }
}