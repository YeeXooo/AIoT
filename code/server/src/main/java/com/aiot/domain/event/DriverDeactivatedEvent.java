package com.aiot.domain.event;

import com.aiot.domain.shared.AggregateId;
import com.aiot.domain.shared.AggregateType;
import com.aiot.domain.shared.DriverId;

import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 驾驶员注销/账号删除事件。
 * 驾驶员注销或账号删除时产出，触发各模块异步收尾。
 */
public record DriverDeactivatedEvent(
        String eventId,
        Instant occurredAt,
        AggregateId aggregateId,
        DriverId driverId,
        Instant deactivationTime
) implements DomainEvent {

    public DriverDeactivatedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(driverId, "driverId must not be null");
        Objects.requireNonNull(deactivationTime, "deactivationTime must not be null");
    }

    public DriverDeactivatedEvent(DriverId driverId, Instant deactivationTime) {
        this(
                java.util.UUID.randomUUID().toString(),
                Instant.now(),
                new AggregateId(driverId.id(), AggregateType.DRIVER),
                driverId,
                deactivationTime
        );
    }
}
