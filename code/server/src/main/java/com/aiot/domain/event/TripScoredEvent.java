package com.aiot.domain.event;

import com.aiot.domain.shared.AggregateId;
import com.aiot.domain.shared.AggregateType;
import com.aiot.domain.shared.TripId;

import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 行程评分完成事件。
 * ScoringService 完成一次行程级评分并通过 TripRepository 写入 TripScore 后发出。
 */
public record TripScoredEvent(
        String eventId,
        Instant occurredAt,
        AggregateId aggregateId,
        TripId tripId,
        int tripScore,
        int fatigueCount,
        int distractionCount,
        int roadRageCount,
        int hardBrakingCount,
        int hardAccelerationCount
) implements DomainEvent {

    public TripScoredEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(tripId, "tripId must not be null");
    }

    public TripScoredEvent(TripId tripId, int tripScore,
                           int fatigueCount, int distractionCount, int roadRageCount,
                           int hardBrakingCount, int hardAccelerationCount) {
        this(
                java.util.UUID.randomUUID().toString(),
                Instant.now(),
                new AggregateId(tripId.id(), AggregateType.TRIP),
                tripId,
                tripScore,
                fatigueCount,
                distractionCount,
                roadRageCount,
                hardBrakingCount,
                hardAccelerationCount
        );
    }
}
