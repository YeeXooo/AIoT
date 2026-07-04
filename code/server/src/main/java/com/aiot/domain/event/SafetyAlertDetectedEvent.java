package com.aiot.domain.event;

import com.aiot.domain.shared.AggregateId;
import com.aiot.domain.shared.AggregateType;
import com.aiot.domain.shared.TripId;

import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 安全告警检测事件。
 * IoTDA AMQP 上报安全告警后发出。
 */
public record SafetyAlertDetectedEvent(
        String eventId,
        Instant occurredAt,
        AggregateId aggregateId,
        TripId tripId,
        String deviceId,
        AlertType alertType,
        RiskLevel riskLevel,
        double latitude,
        double longitude,
        Instant alertTime,
        String alertMessage
) implements DomainEvent {

    public SafetyAlertDetectedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(tripId, "tripId must not be null");
        Objects.requireNonNull(deviceId, "deviceId must not be null");
        Objects.requireNonNull(alertType, "alertType must not be null");
        Objects.requireNonNull(riskLevel, "riskLevel must not be null");
        Objects.requireNonNull(alertTime, "alertTime must not be null");
        Objects.requireNonNull(alertMessage, "alertMessage must not be null");
    }

    public SafetyAlertDetectedEvent(TripId tripId, String deviceId, AlertType alertType, RiskLevel riskLevel,
                                    double latitude, double longitude, Instant alertTime, String alertMessage) {
        this(
                java.util.UUID.randomUUID().toString(),
                Instant.now(),
                new AggregateId(tripId.id(), AggregateType.TRIP),
                tripId,
                deviceId,
                alertType,
                riskLevel,
                latitude,
                longitude,
                alertTime,
                alertMessage
        );
    }
}
