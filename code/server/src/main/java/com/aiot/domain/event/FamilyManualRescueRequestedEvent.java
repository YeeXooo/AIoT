package com.aiot.domain.event;

import com.aiot.domain.shared.AccountId;
import com.aiot.domain.shared.AggregateId;
import com.aiot.domain.shared.AggregateType;
import com.aiot.domain.shared.DriverId;

import java.time.Instant;
import java.util.Objects;

/**
 * 家属手动救援请求事件。
 * 家属端在视频巡视时一键触发应急救援联动时产出。
 */
public record FamilyManualRescueRequestedEvent(
        String eventId,
        Instant occurredAt,
        AggregateId aggregateId,
        DriverId driverId,
        AccountId requesterAccountId,
        double latitude,
        double longitude,
        Instant requestTime
) implements DomainEvent {

    public FamilyManualRescueRequestedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(driverId, "driverId must not be null");
        Objects.requireNonNull(requesterAccountId, "requesterAccountId must not be null");
        Objects.requireNonNull(requestTime, "requestTime must not be null");
    }

    public FamilyManualRescueRequestedEvent(DriverId driverId, AccountId requesterAccountId,
                                             double latitude, double longitude, Instant requestTime) {
        this(
                java.util.UUID.randomUUID().toString(),
                Instant.now(),
                new AggregateId(driverId.id(), AggregateType.DRIVER),
                driverId,
                requesterAccountId,
                latitude,
                longitude,
                requestTime
        );
    }
}
