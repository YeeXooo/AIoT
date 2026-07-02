package com.aiot.domain.model;

import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * 活体检测判定窗口（VO-20）
 * 活体检出会话的窗口状态，支撑纯函数式判定逻辑
 */
@Embeddable
@Getter
@EqualsAndHashCode
public final class DetectionWindow {
    private final Duration remainingTime;
    private final Instant startTime;
    private final int microMovementCount;
    private final Duration toleranceThreshold;

    private DetectionWindow(Duration remainingTime, Instant startTime,
                            int microMovementCount, Duration toleranceThreshold) {
        if (remainingTime == null || remainingTime.isNegative())
            throw new IllegalArgumentException("剩余时长不合法");
        if (startTime == null) { throw new IllegalArgumentException("起始时间不能为空"); }
        if (toleranceThreshold == null || toleranceThreshold.isNegative())
            throw new IllegalArgumentException("容差阈值不合法");
        this.remainingTime = remainingTime;
        this.startTime = startTime;
        this.microMovementCount = Math.max(microMovementCount, 0);
        this.toleranceThreshold = toleranceThreshold;
    }

    public static DetectionWindow create(Duration totalDuration, Instant startTime, Duration tolerance) {
        return new DetectionWindow(totalDuration, startTime, 0, tolerance);
    }

    public DetectionWindow tick(Duration delta) {
        Duration newRemaining = remainingTime.minus(delta);
        if (newRemaining.isNegative()) { newRemaining = Duration.ZERO; }
        return new DetectionWindow(newRemaining, startTime, microMovementCount, toleranceThreshold);
    }

    public DetectionWindow incrementCount() {
        return new DetectionWindow(remainingTime, startTime, microMovementCount + 1, toleranceThreshold);
    }

    public boolean isExpired() {
        return remainingTime.isZero();
    }

    protected DetectionWindow() {
        this.remainingTime = Duration.ZERO;
        this.startTime = Instant.EPOCH;
        this.microMovementCount = 0;
        this.toleranceThreshold = Duration.ZERO;
    }
}