package com.aiot.domain.event;

import com.aiot.domain.shared.AggregateId;
import com.aiot.domain.shared.AggregateType;
import com.aiot.domain.shared.VehicleId;

import java.time.Instant;
import java.util.Objects;

public record OTAUpgradeStartedEvent(
        String eventId,
        Instant occurredAt,
        AggregateId aggregateId,
        VehicleId vehicleId,
        String targetVersion,
        Instant startTime
) implements DomainEvent {

    public OTAUpgradeStartedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(vehicleId, "vehicleId must not be null");
        Objects.requireNonNull(targetVersion, "targetVersion must not be null");
        Objects.requireNonNull(startTime, "startTime must not be null");
    }

    public OTAUpgradeStartedEvent(VehicleId vehicleId, String targetVersion, Instant startTime) {
        this(
                java.util.UUID.randomUUID().toString(),
                Instant.now(),
                new AggregateId(vehicleId.id(), AggregateType.VEHICLE),
                vehicleId,
                targetVersion,
                startTime
        );
    }
}
