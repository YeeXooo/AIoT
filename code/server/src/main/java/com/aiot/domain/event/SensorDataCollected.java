package com.aiot.domain.event;

import com.aiot.domain.model.SensorReading;
import com.aiot.domain.shared.AggregateId;
import com.aiot.domain.shared.AggregateType;
import com.aiot.domain.shared.TripId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 传感器数据采集事件。
 * IoTDA AMQP 上报车辆传感器读数后发出。
 */
public record SensorDataCollected(
        String eventId,
        Instant occurredAt,
        AggregateId aggregateId,
        TripId tripId,
        String deviceId,
        List<SensorReading> readings
) implements DomainEvent {

    public SensorDataCollected {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(tripId, "tripId must not be null");
        Objects.requireNonNull(deviceId, "deviceId must not be null");
        Objects.requireNonNull(readings, "readings must not be null");
    }

    public SensorDataCollected(TripId tripId, String deviceId, List<SensorReading> readings) {
        this(
                java.util.UUID.randomUUID().toString(),
                Instant.now(),
                new AggregateId(tripId.id(), AggregateType.TRIP),
                tripId,
                deviceId,
                readings
        );
    }
}
