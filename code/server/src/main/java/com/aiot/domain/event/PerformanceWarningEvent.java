package com.aiot.domain.event;

import com.aiot.domain.shared.AggregateId;
import com.aiot.domain.shared.AggregateType;
import com.aiot.domain.shared.DriverId;

import java.time.Instant;
import java.util.Objects;

/**
 * 绩效预警事件。
 * 行程级或周期级评分 < 60 时产出。
 */
public record PerformanceWarningEvent(
        String eventId,
        Instant occurredAt,
        AggregateId aggregateId,
        DriverId driverId,
        int score,
        String evaluationPeriod,
        String mainDeductionItems,
        Instant warningTime
) implements DomainEvent {

    public PerformanceWarningEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(driverId, "driverId must not be null");
        Objects.requireNonNull(evaluationPeriod, "evaluationPeriod must not be null");
        Objects.requireNonNull(mainDeductionItems, "mainDeductionItems must not be null");
        Objects.requireNonNull(warningTime, "warningTime must not be null");
    }

    public PerformanceWarningEvent(DriverId driverId, int score, String evaluationPeriod,
                                    String mainDeductionItems, Instant warningTime) {
        this(
                java.util.UUID.randomUUID().toString(),
                Instant.now(),
                new AggregateId(driverId.id(), AggregateType.DRIVER),
                driverId,
                score,
                evaluationPeriod,
                mainDeductionItems,
                warningTime
        );
    }
}
