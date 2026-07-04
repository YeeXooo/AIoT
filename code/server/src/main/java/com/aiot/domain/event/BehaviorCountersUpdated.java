package com.aiot.domain.event;

import com.aiot.domain.model.DrivingBehaviorCounters;
import com.aiot.domain.shared.AggregateId;
import com.aiot.domain.shared.AggregateType;
import com.aiot.domain.shared.TripId;

import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 驾驶行为计数器更新事件。
 * IoTDA AMQP 上报驾驶行为统计数据后发出。
 */
public record BehaviorCountersUpdated(
        String eventId,
        Instant occurredAt,
        AggregateId aggregateId,
        TripId tripId,
        String deviceId,
        DrivingBehaviorCounters counters
) implements DomainEvent {

    public BehaviorCountersUpdated {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(tripId, "tripId must not be null");
        Objects.requireNonNull(deviceId, "deviceId must not be null");
        Objects.requireNonNull(counters, "counters must not be null");
    }

    public BehaviorCountersUpdated(TripId tripId, String deviceId, DrivingBehaviorCounters counters) {
        this(
                java.util.UUID.randomUUID().toString(),
                Instant.now(),
                new AggregateId(tripId.id(), AggregateType.TRIP),
                tripId,
                deviceId,
                counters
        );
    }
}
