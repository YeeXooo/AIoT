package com.aiot.domain.model;

import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 车辆状态快照值对象。
 * <p>
 * 事故前 30 秒或特定时刻的车辆状态摘要。
 * </p>
 * <p>
 * 设计依据：docs/ood_domain.md VO-09
 * </p>
 */
public record VehicleStateSnapshot(
        Instant timestamp,
        Double speed,
        Double acceleration,
        Boolean doorLocked,
        Boolean fireRisk,
        Boolean fuelLeak,
        Double latitude,
        Double longitude,
        Integer gpsFix
) {
    public VehicleStateSnapshot {
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }

    public VehicleStateSnapshot(Instant timestamp, Double speed, Double acceleration,
            Boolean doorLocked, Boolean fireRisk, Boolean fuelLeak,
            Double latitude, Double longitude) {
        this(timestamp, speed, acceleration, doorLocked, fireRisk, fuelLeak,
             latitude, longitude, null);
    }
}
