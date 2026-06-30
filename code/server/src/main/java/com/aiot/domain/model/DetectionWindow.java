package com.aiot.domain.model;

import com.aiot.domain.model.exception.BusinessException;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import java.time.Duration;
import java.time.Instant;

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
        if (remainingTime == null || remainingTime.isNegative()) {
            throw new BusinessException(
                    "MODEL_041",
                    "检测窗口剩余时长不能为负数",
                    "DETECTION_WINDOW_VALIDATE"
            );
        }
        if (startTime == null) {
            throw new BusinessException(
                    "MODEL_042",
                    "检测窗口起始时间不能为空",
                    "DETECTION_WINDOW_VALIDATE"
            );
        }
        if (toleranceThreshold == null || toleranceThreshold.isNegative()) {
            throw new BusinessException(
                    "MODEL_043",
                    "检测窗口容差阈值不能为负数",
                    "DETECTION_WINDOW_VALIDATE"
            );
        }
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
        if (newRemaining.isNegative()) newRemaining = Duration.ZERO;
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