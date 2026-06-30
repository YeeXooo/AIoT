package com.aiot.domain.event;

import com.aiot.domain.shared.AggregateId;
import com.aiot.domain.shared.AggregateType;
import com.aiot.domain.shared.DriverId;

import java.time.Instant;
import java.util.Objects;

/**
 * 驾驶员综合风险评分更新事件。
 * ScoringService 完成 Driver 级综合风险评分计算后产出。
 */
public record DriverScoreUpdatedEvent(
        String eventId,
        Instant occurredAt,
        AggregateId aggregateId,
        DriverId driverId,
        int newScore,
        int oldScore,
        String evaluationPeriod,  // 计算周期，如 "2026-06"、"LAST_30_DAYS"
        Instant calculationTime
) implements DomainEvent {

    public DriverScoreUpdatedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(driverId, "driverId must not be null");
        Objects.requireNonNull(evaluationPeriod, "evaluationPeriod must not be null");
        Objects.requireNonNull(calculationTime, "calculationTime must not be null");
    }

    public DriverScoreUpdatedEvent(DriverId driverId, int newScore, int oldScore,
                                    String evaluationPeriod, Instant calculationTime) {
        this(
                java.util.UUID.randomUUID().toString(),
                Instant.now(),
                new AggregateId(driverId.id(), AggregateType.DRIVER),
                driverId,
                newScore,
                oldScore,
                evaluationPeriod,
                calculationTime
        );
    }
}
