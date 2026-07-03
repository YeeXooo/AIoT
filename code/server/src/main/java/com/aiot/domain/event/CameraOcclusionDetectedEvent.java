package com.aiot.domain.event;

import com.aiot.domain.shared.AggregateId;
import com.aiot.domain.shared.AggregateType;
import com.aiot.domain.shared.VehicleId;

import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 摄像头遮挡检测事件。
 * SensorSelfCheckService 通过感知通道检测到摄像头被物理遮挡时产出。
 */
public record CameraOcclusionDetectedEvent(
        String eventId,
        Instant occurredAt,
        AggregateId aggregateId,
        VehicleId vehicleId,
        String occludedSensorId,
        Instant detectionTime
) implements DomainEvent {

    public CameraOcclusionDetectedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(vehicleId, "vehicleId must not be null");
        Objects.requireNonNull(occludedSensorId, "occludedSensorId must not be null");
        Objects.requireNonNull(detectionTime, "detectionTime must not be null");
    }

    public CameraOcclusionDetectedEvent(VehicleId vehicleId, String occludedSensorId, Instant detectionTime) {
        this(
                java.util.UUID.randomUUID().toString(),
                Instant.now(),
                new AggregateId(vehicleId.id(), AggregateType.VEHICLE),
                vehicleId,
                occludedSensorId,
                detectionTime
        );
    }
}
