package com.aiot.domain.event;

import com.aiot.domain.model.VehicleStateSnapshot;
import com.aiot.domain.shared.AggregateId;
import com.aiot.domain.shared.AggregateType;
import com.aiot.domain.shared.TripId;

import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 车辆状态更新事件。
 * IoTDA AMQP 上报车辆状态快照后发出。
 */
public record VehicleStateUpdated(
        String eventId,
        Instant occurredAt,
        AggregateId aggregateId,
        TripId tripId,
        String deviceId,
        VehicleStateSnapshot state
) implements DomainEvent {

    public VehicleStateUpdated {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(tripId, "tripId must not be null");
        Objects.requireNonNull(deviceId, "deviceId must not be null");
        Objects.requireNonNull(state, "state must not be null");
    }

    public VehicleStateUpdated(TripId tripId, String deviceId, VehicleStateSnapshot state) {
        this(
                java.util.UUID.randomUUID().toString(),
                Instant.now(),
                new AggregateId(tripId.id(), AggregateType.TRIP),
                tripId,
                deviceId,
                state
        );
    }
}
