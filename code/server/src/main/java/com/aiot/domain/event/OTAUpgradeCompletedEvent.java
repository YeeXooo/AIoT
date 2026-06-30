package com.aiot.domain.event;

import com.aiot.domain.shared.AggregateId;
import com.aiot.domain.shared.AggregateType;
import com.aiot.domain.shared.VehicleId;

import java.time.Instant;
import java.util.Objects;

/**
 * OTA 升级完成事件。
 * 车载终端固件升级成功时产出。
 */
public record OTAUpgradeCompletedEvent(
        String eventId,
        Instant occurredAt,
        AggregateId aggregateId,
        VehicleId vehicleId,
        String oldVersion,
        String newVersion,
        long upgradeDurationMs,
        Instant completionTime
) implements DomainEvent {

    public OTAUpgradeCompletedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(vehicleId, "vehicleId must not be null");
        Objects.requireNonNull(oldVersion, "oldVersion must not be null");
        Objects.requireNonNull(newVersion, "newVersion must not be null");
        Objects.requireNonNull(completionTime, "completionTime must not be null");
    }

    public OTAUpgradeCompletedEvent(VehicleId vehicleId, String oldVersion, String newVersion,
                                     long upgradeDurationMs, Instant completionTime) {
        this(
                java.util.UUID.randomUUID().toString(),
                Instant.now(),
                new AggregateId(vehicleId.id(), AggregateType.VEHICLE),
                vehicleId,
                oldVersion,
                newVersion,
                upgradeDurationMs,
                completionTime
        );
    }
}
