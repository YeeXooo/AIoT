package com.aiot.domain.event;

import com.aiot.domain.shared.AggregateId;
import com.aiot.domain.shared.AggregateType;
import com.aiot.domain.shared.VehicleId;

import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 活体遗留检测事件。
 * LifeDetectionService 在 60s 窗口内判定成立时产出。
 */
public record LifeDetectedEvent(
        String eventId,
        Instant occurredAt,
        AggregateId aggregateId,
        VehicleId vehicleId,
        double confidence,
        Instant detectionTime
) implements DomainEvent {

    public LifeDetectedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(vehicleId, "vehicleId must not be null");
        Objects.requireNonNull(detectionTime, "detectionTime must not be null");
    }

    public LifeDetectedEvent(VehicleId vehicleId, double confidence, Instant detectionTime) {
        this(
                java.util.UUID.randomUUID().toString(),
                Instant.now(),
                new AggregateId(vehicleId.id(), AggregateType.VEHICLE),
                vehicleId,
                confidence,
                detectionTime
        );
    }
}
