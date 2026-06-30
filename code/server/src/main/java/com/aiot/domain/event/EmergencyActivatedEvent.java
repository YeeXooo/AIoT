package com.aiot.domain.event;

import com.aiot.domain.shared.AggregateId;
import com.aiot.domain.shared.AggregateType;
import com.aiot.domain.shared.DriverId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 碰撞失能事件。
 * EmergencyResponseService 判定 BR-06 碰撞+失能条件满足时产出。
 */
public record EmergencyActivatedEvent(
        String eventId,
        Instant occurredAt,
        AggregateId aggregateId,
        DriverId driverId,
        double latitude,
        double longitude,
        List<VehicleStateSnapshot> vehicleStateSnapshots,  // 事故前 30 秒车辆状态快照序列
        Instant eventTime
) implements DomainEvent {

    public EmergencyActivatedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(driverId, "driverId must not be null");
        Objects.requireNonNull(vehicleStateSnapshots, "vehicleStateSnapshots must not be null");
        Objects.requireNonNull(eventTime, "eventTime must not be null");
    }

    public EmergencyActivatedEvent(DriverId driverId, double latitude, double longitude,
                                    List<VehicleStateSnapshot> vehicleStateSnapshots, Instant eventTime) {
        this(
                java.util.UUID.randomUUID().toString(),
                Instant.now(),
                new AggregateId(driverId.id(), AggregateType.DRIVER),
                driverId,
                latitude,
                longitude,
                vehicleStateSnapshots,
                eventTime
        );
    }
}
