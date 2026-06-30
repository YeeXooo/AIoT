package com.aiot.domain.event;

import com.aiot.domain.shared.AggregateId;
import com.aiot.domain.shared.AggregateType;
import com.aiot.domain.shared.VehicleId;

import java.time.Instant;
import java.util.Objects;
import java.util.List;

/**
 * 传感器故障事件。
 * SensorSelfCheckService 检测到关键传感器故障时产出。
 */
public record SensorFailureEvent(
        String eventId,
        Instant occurredAt,
        AggregateId aggregateId,
        VehicleId vehicleId,
        List<String> failedSensors,
        Instant failureTime
) implements DomainEvent {

    public SensorFailureEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(vehicleId, "vehicleId must not be null");
        Objects.requireNonNull(failedSensors, "failedSensors must not be null");
        Objects.requireNonNull(failureTime, "failureTime must not be null");
    }

    public SensorFailureEvent(VehicleId vehicleId, List<String> failedSensors, Instant failureTime) {
        this(
                java.util.UUID.randomUUID().toString(),
                Instant.now(),
                new AggregateId(vehicleId.id(), AggregateType.VEHICLE),
                vehicleId,
                failedSensors,
                failureTime
        );
    }
}
