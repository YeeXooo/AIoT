package com.aiot.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 时间范围值对象。
 * <p>
 * 表示一段连续的时间区间（起止时间戳）。
 * </p>
 * <p>
 * 设计依据：docs/ood_domain.md VO-10
 * </p>
 */
public record TimeRange(Instant from, Instant to) {

    public TimeRange {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
    }

    /**
     * 计算时间范围的持续时间（秒）。
     */
    public long durationSeconds() {
        return to.getEpochSecond() - from.getEpochSecond();
    }
<<<<<<< HEAD
}
=======
}

>>>>>>> d61a4a60204c7e68e9b5b3ec725a630abc2e642a
