package com.aiot.domain.event;

import com.aiot.domain.shared.AggregateId;
import com.aiot.domain.shared.AggregateType;
import com.aiot.domain.shared.AlertId;
import com.aiot.domain.shared.TripId;

import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 告警触发事件。
 * AlertPersistenceService 通过 TripRepository 创建 SafetyAlertEvent 后发出。
 */
public record AlertTriggeredEvent(
        String eventId,
        Instant occurredAt,
        AggregateId aggregateId,
        AlertId alertId,
        TripId tripId,
        AlertType alertType,
        RiskLevel riskLevel,
        double latitude,
        double longitude,
        Instant alertTime
) implements DomainEvent {

    public AlertTriggeredEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(alertId, "alertId must not be null");
        Objects.requireNonNull(tripId, "tripId must not be null");
        Objects.requireNonNull(alertType, "alertType must not be null");
        Objects.requireNonNull(riskLevel, "riskLevel must not be null");
        Objects.requireNonNull(alertTime, "alertTime must not be null");
    }

    public AlertTriggeredEvent(AlertId alertId, TripId tripId, AlertType alertType, RiskLevel riskLevel,
                               double latitude, double longitude, Instant alertTime) {
        this(
                java.util.UUID.randomUUID().toString(),
                Instant.now(),
                new AggregateId(tripId.id(), AggregateType.TRIP),
                alertId,
                tripId,
                alertType,
                riskLevel,
                latitude,
                longitude,
                alertTime
        );
    }
}
