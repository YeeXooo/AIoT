package com.aiot.domain.model;

import com.aiot.domain.model.exception.BusinessException;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import java.time.Instant;

/**
 * 车辆状态快照（VO-09）
 * 事故前时间窗内的车辆状态切面，用于应急救援上报
 */
@Embeddable
@Getter
@EqualsAndHashCode
public final class VehicleStateSnapshot {
    private final Instant timestamp;
    private final double speed;
    private final double acceleration;
    private final boolean doorLocked;
    private final boolean fireRisk;
    private final boolean fuelLeak;

    private VehicleStateSnapshot(Instant timestamp, double speed, double acceleration,
                                 boolean doorLocked, boolean fireRisk, boolean fuelLeak) {
        if (timestamp == null) {
            throw new BusinessException(
                    "MODEL_025",
                    "车辆状态快照时间戳不能为空",
                    "VEHICLE_STATE_SNAPSHOT_VALIDATE"
            );
        }
        this.timestamp = timestamp;
        this.speed = speed;
        this.acceleration = acceleration;
        this.doorLocked = doorLocked;
        this.fireRisk = fireRisk;
        this.fuelLeak = fuelLeak;
    }

    public static VehicleStateSnapshot of(Instant timestamp, double speed, double acceleration,
                                          boolean doorLocked, boolean fireRisk, boolean fuelLeak) {
        return new VehicleStateSnapshot(timestamp, speed, acceleration, doorLocked, fireRisk, fuelLeak);
    }

    protected VehicleStateSnapshot() {
        this.timestamp = Instant.EPOCH;
        this.speed = 0;
        this.acceleration = 0;
        this.doorLocked = false;
        this.fireRisk = false;
        this.fuelLeak = false;
    }
}