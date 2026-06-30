package com.aiot.domain.model;

import com.aiot.domain.model.exception.BusinessException;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import java.time.Instant;

/**
 * 时间范围（VO-10）
 * 连续时间区间，用于查询周期、快照回取参数
 */
@Embeddable
@Getter
@EqualsAndHashCode
public final class TimeRange {
    private final Instant startTime;
    private final Instant endTime;

    private TimeRange(Instant startTime, Instant endTime) {
        if (startTime == null || endTime == null) {
            throw new BusinessException(
                    "MODEL_026",
                    "时间范围起止时间不能为空",
                    "TIME_RANGE_VALIDATE"
            );
        }
        if (startTime.isAfter(endTime)) {
            throw new BusinessException(
                    "MODEL_027",
                    "起始时间不能晚于结束时间",
                    "TIME_RANGE_VALIDATE"
            );
        }
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