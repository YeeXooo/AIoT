package com.aiot.domain.event;

import com.aiot.domain.shared.AggregateId;
import com.aiot.domain.shared.AggregateType;
import com.aiot.domain.shared.VehicleId;

import java.time.Instant;
import java.util.Objects;

/**
 * 摄像头遮挡移除事件。
 * SensorSelfCheckService 检测到摄像头物理遮挡已移除时产出。
 */
public record CameraOcclusionRemovedEvent(
        String eventId,
        Instant occurredAt,
        AggregateId aggregateId,
        VehicleId vehicleId,
        String restoredSensorId,
        Instant removalTime
) implements DomainEvent {

    public CameraOcclusionRemovedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(vehicleId, "vehicleId must not be null");
        Objects.requireNonNull(restoredSensorId, "restoredSensorId must not be null");
        Objects.requireNonNull(removalTime, "removalTime must not be null");
    }

    public CameraOcclusionRemovedEvent(VehicleId vehicleId, String restoredSensorId, Instant removalTime) {
        this(
                java.util.UUID.randomUUID().toString(),
                Instant.now(),
                new AggregateId(vehicleId.id(), AggregateType.VEHICLE),
                vehicleId,
                restoredSensorId,
                removalTime
        );
    }
}
