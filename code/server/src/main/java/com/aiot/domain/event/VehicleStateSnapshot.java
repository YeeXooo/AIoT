package com.aiot.domain.event;

import java.time.Instant;
import java.util.Objects;

/**
 * 车辆状态快照（临时占位类型）。
 * 用于 EmergencyActivatedEvent 携带事故前 30 秒的车辆状态。
 * 待 domain.model 中 VO-09 正式值对象实现后替换。
 */
public record VehicleStateSnapshot(
        Instant timestamp,
        double speed,
        double acceleration,
        boolean doorLocked,
        boolean fireRisk,
        boolean fuelLeak
) {
    public VehicleStateSnapshot {
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }
}
