package com.aiot.domain.event;

import com.aiot.domain.shared.AggregateId;
import com.aiot.domain.shared.AggregateType;
import com.aiot.domain.shared.TripId;

import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 风险判定成立事件。
 * RiskDeterminationService 完成一次流式融合判定后产出。
 * AlertType 仅限 {FATIGUE, DISTRACTION, ROAD_RAGE}。
 */
public record RiskDeterminedEvent(
        String eventId,
        Instant occurredAt,
        AggregateId aggregateId,
        TripId tripId,
        RiskLevel riskLevel,
        AlertType alertType,
        Instant detectionTime,
        String anomalyDescription  // 异常特征快照
) implements DomainEvent {

    public RiskDeterminedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(tripId, "tripId must not be null");
        Objects.requireNonNull(riskLevel, "riskLevel must not be null");
        Objects.requireNonNull(alertType, "alertType must not be null");
        Objects.requireNonNull(detectionTime, "detectionTime must not be null");
        Objects.requireNonNull(anomalyDescription, "anomalyDescription must not be null");
    }

    public RiskDeterminedEvent(TripId tripId, RiskLevel riskLevel, AlertType alertType,
                               Instant detectionTime, String anomalyDescription) {
        this(
                java.util.UUID.randomUUID().toString(),
                Instant.now(),
                new AggregateId(tripId.id(), AggregateType.TRIP),
                tripId,
                riskLevel,
                alertType,
                detectionTime,
                anomalyDescription
        );
    }
}
