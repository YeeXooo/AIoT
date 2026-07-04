package com.aiot.domain.event;

import com.aiot.domain.model.PhysiologicalSnapshot;
import com.aiot.domain.shared.AggregateId;
import com.aiot.domain.shared.AggregateType;
import com.aiot.domain.shared.TripId;

import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 生理数据更新事件。
 * IoTDA AMQP 上报驾驶员生理指标快照后发出。
 */
public record PhysiologicalDataUpdated(
        String eventId,
        Instant occurredAt,
        AggregateId aggregateId,
        TripId tripId,
        String deviceId,
        PhysiologicalSnapshot snapshot
) implements DomainEvent {

    public PhysiologicalDataUpdated {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(tripId, "tripId must not be null");
        Objects.requireNonNull(deviceId, "deviceId must not be null");
        Objects.requireNonNull(snapshot, "snapshot must not be null");
    }

    public PhysiologicalDataUpdated(TripId tripId, String deviceId, PhysiologicalSnapshot snapshot) {
        this(
                java.util.UUID.randomUUID().toString(),
                Instant.now(),
                new AggregateId(tripId.id(), AggregateType.TRIP),
                tripId,
                deviceId,
                snapshot
        );
    }
}
