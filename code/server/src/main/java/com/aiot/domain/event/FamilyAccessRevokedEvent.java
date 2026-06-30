package com.aiot.domain.event;

import com.aiot.domain.shared.AccountId;
import com.aiot.domain.shared.AggregateId;
import com.aiot.domain.shared.AggregateType;
import com.aiot.domain.shared.DriverId;

import java.time.Instant;
import java.util.Objects;

/**
 * 家属权限撤销事件。
 * 家属对某 Driver 的常规权限被撤销时产出。
 */
public record FamilyAccessRevokedEvent(
        String eventId,
        Instant occurredAt,
        AggregateId aggregateId,
        AccountId accountId,
        DriverId driverId,
        AccessRevocationReason reason,
        Instant revokedTime
) implements DomainEvent {

    public FamilyAccessRevokedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(driverId, "driverId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(revokedTime, "revokedTime must not be null");
    }

    public FamilyAccessRevokedEvent(AccountId accountId, DriverId driverId,
                                     AccessRevocationReason reason, Instant revokedTime) {
        this(
                java.util.UUID.randomUUID().toString(),
                Instant.now(),
                new AggregateId(accountId.id(), AggregateType.SYSTEM_ACCOUNT),
                accountId,
                driverId,
                reason,
                revokedTime
        );
    }
}
