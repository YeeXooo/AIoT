package com.aiot.domain.event;

import com.aiot.domain.shared.AggregateId;
import com.aiot.domain.shared.AggregateType;
import com.aiot.domain.shared.VehicleId;

import java.time.Instant;
import java.util.Objects;

/**
 * 车辆熄火且车门落锁事件。
 * 由车辆状态变更产生（基础设施层信号），触发 LifeDetectionService 启动活体判定窗口。
 */
public record VehicleIgnitionOffLockedEvent(
        String eventId,
        Instant occurredAt,
        AggregateId aggregateId,
        VehicleId vehicleId,
        Instant eventTime
) implements DomainEvent {

    public VehicleIgnitionOffLockedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(vehicleId, "vehicleId must not be null");
        Objects.requireNonNull(eventTime, "eventTime must not be null");
    }

    public VehicleIgnitionOffLockedEvent(VehicleId vehicleId, Instant eventTime) {
        this(
                java.util.UUID.randomUUID().toString(),
                Instant.now(),
                new AggregateId(vehicleId.id(), AggregateType.VEHICLE),
                vehicleId,
                eventTime
        );
    }
}
