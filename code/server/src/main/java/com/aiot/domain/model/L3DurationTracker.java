package com.aiot.domain.model;

import com.aiot.domain.model.exception.BusinessException;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import java.time.Duration;
import java.time.Instant;

/**
 * L3持续时长追踪器（VO-17）
 * 追踪L3风险累计时长，用于60秒授权触发规则
 */
@Embeddable
@Getter
@EqualsAndHashCode
public final class L3DurationTracker {
    private final Instant startTime;
    private final Duration accumulatedDuration;
    private final boolean active;

    private L3DurationTracker(Instant startTime, Duration accumulatedDuration, boolean active) {
        if (startTime == null) {
            throw new BusinessException(
                    "MODEL_036",
                    "L3追踪起始时间不能为空",
                    "L3_DURATION_TRACKER_VALIDATE"
            );
        }
        if (accumulatedDuration == null || accumulatedDuration.isNegative()) {
            throw new BusinessException(
                    "MODEL_037",
                    "L3累计时长不能为负数",
                    "L3_DURATION_TRACKER_VALIDATE"
            );
        }
        this.startTime = startTime;
        this.accumulatedDuration = accumulatedDuration;
        this.active = active;
    }

    public static L3DurationTracker start(Instant startTime) {
        return new L3DurationTracker(startTime, Duration.ZERO, true);
    }

    public L3DurationTracker advance(Instant now) {
        if (!active) return this;
        Duration delta = Duration.between(startTime.plus(accumulatedDuration), now);
        if (delta.isNegative()) delta = Duration.ZERO;
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
<<<<<<< HEAD
=======

>>>>>>> d61a4a60204c7e68e9b5b3ec725a630abc2e642a
}