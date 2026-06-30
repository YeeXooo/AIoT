package com.aiot.domain.model;

import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import java.time.Instant;

/**
 * 时间范围（VO-10）
 * 连续时间区间，用于查询周期、快照回取参数；强制起始不晚于结束
 */
@Embeddable
@Getter
@EqualsAndHashCode
public final class TimeRange {
    private final Instant startTime;
    private final Instant endTime;

    private TimeRange(Instant startTime, Instant endTime) {
        if (startTime == null || endTime == null) throw new IllegalArgumentException("时间不能为空");
        if (startTime.isAfter(endTime)) throw new IllegalArgumentException("起始时间不能晚于结束时间");
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public static TimeRange of(Instant startTime, Instant endTime) {
        return new TimeRange(startTime, endTime);
    }

    protected TimeRange() {
        this.startTime = Instant.EPOCH;
        this.endTime = Instant.EPOCH;
    }
}