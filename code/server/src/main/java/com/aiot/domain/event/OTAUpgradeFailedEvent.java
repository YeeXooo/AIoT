package com.aiot.domain.event;

import com.aiot.domain.shared.AggregateId;
import com.aiot.domain.shared.AggregateType;
import com.aiot.domain.shared.VehicleId;

import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * OTA 升级失败事件。
 * OTA 升级因校验/传输失败回滚时产出。
 */
public record OTAUpgradeFailedEvent(
        String eventId,
        Instant occurredAt,
        AggregateId aggregateId,
        VehicleId vehicleId,
        String targetVersion,
        String failureStage,
        String failureReason,
        Instant failureTime
) implements DomainEvent {

    public OTAUpgradeFailedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(vehicleId, "vehicleId must not be null");
        Objects.requireNonNull(targetVersion, "targetVersion must not be null");
        Objects.requireNonNull(failureStage, "failureStage must not be null");
        Objects.requireNonNull(failureReason, "failureReason must not be null");
        Objects.requireNonNull(failureTime, "failureTime must not be null");
    }

    public OTAUpgradeFailedEvent(VehicleId vehicleId, String targetVersion,
                                  String failureStage, String failureReason, Instant failureTime) {
        this(
                java.util.UUID.randomUUID().toString(),
                Instant.now(),
                new AggregateId(vehicleId.id(), AggregateType.VEHICLE),
                vehicleId,
                targetVersion,
                failureStage,
                failureReason,
                failureTime
        );
    }
}
