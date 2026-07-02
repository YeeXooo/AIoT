package com.aiot.domain.event;

import com.aiot.domain.shared.AggregateId;
import com.aiot.domain.shared.AggregateType;
import com.aiot.domain.shared.VehicleId;

import java.time.Instant;
import java.util.Objects;

public record OTAUpgradeRolledBackEvent(
        String eventId,
        Instant occurredAt,
        AggregateId aggregateId,
        VehicleId vehicleId,
        String targetVersion,
        String rollbackStage,
        String rollbackReason,
        Instant rollbackTime
) implements DomainEvent {

    public OTAUpgradeRolledBackEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(vehicleId, "vehicleId must not be null");
        Objects.requireNonNull(targetVersion, "targetVersion must not be null");
        Objects.requireNonNull(rollbackStage, "rollbackStage must not be null");
        Objects.requireNonNull(rollbackReason, "rollbackReason must not be null");
        Objects.requireNonNull(rollbackTime, "rollbackTime must not be null");
    }

    public OTAUpgradeRolledBackEvent(VehicleId vehicleId, String targetVersion,
                                      String rollbackStage, String rollbackReason, Instant rollbackTime) {
        this(
                java.util.UUID.randomUUID().toString(),
                Instant.now(),
                new AggregateId(vehicleId.id(), AggregateType.VEHICLE),
                vehicleId,
                targetVersion,
                rollbackStage,
                rollbackReason,
                rollbackTime
        );
    }
}
