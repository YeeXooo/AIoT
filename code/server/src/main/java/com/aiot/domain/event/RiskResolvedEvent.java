package com.aiot.domain.event;

import com.aiot.domain.shared.AggregateId;
import com.aiot.domain.shared.AggregateType;
import com.aiot.domain.shared.TripId;

import java.time.Instant;
import java.util.Objects;

/**
 * 风险解除事件。
 * RiskDeterminationService 判定某类此前成立的流式风险不再持续时产出。
 */
public record RiskResolvedEvent(
        String eventId,
        Instant occurredAt,
        AggregateId aggregateId,
        TripId tripId,
        AlertType resolvedAlertType,
        Instant resolvedTime
) implements DomainEvent {

    public RiskResolvedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(tripId, "tripId must not be null");
        Objects.requireNonNull(resolvedAlertType, "resolvedAlertType must not be null");
        Objects.requireNonNull(resolvedTime, "resolvedTime must not be null");
    }

    public RiskResolvedEvent(TripId tripId, AlertType resolvedAlertType, Instant resolvedTime) {
        this(
                java.util.UUID.randomUUID().toString(),
                Instant.now(),
                new AggregateId(tripId.id(), AggregateType.TRIP),
                tripId,
                resolvedAlertType,
                resolvedTime
        );
    }
}
