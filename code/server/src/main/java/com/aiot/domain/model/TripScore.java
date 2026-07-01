package com.aiot.domain.model;

import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 行程评分（VO-05）
 * 单次行程驾驶行为评分，强制约束 [0,100] 范围
 */
@Embeddable
@Getter
@EqualsAndHashCode
public final class TripScore {
    private final int value;

    private TripScore(int value) {
        if (value < 0 || value > 100) throw new IllegalArgumentException("评分必须在 0~100 范围内");
        this.value = value;
    }

    public static TripScore of(int value) {
        return new TripScore(value);
    }

    protected TripScore() {
        this.value = 0;
    }
}