package com.aiot.domain.event;

import com.aiot.domain.shared.AccountId;
import com.aiot.domain.shared.AggregateId;
import com.aiot.domain.shared.AggregateType;
import com.aiot.domain.shared.DriverId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 家属权限授予事件。
 * 家属获得对某 Driver 的访问权限时产出（常规 60s 或自动激活、或物理遮挡解除恢复）。
 */
public record FamilyAccessGrantedEvent(
        String eventId,
        Instant occurredAt,
        AggregateId aggregateId,
        AccountId accountId,
        DriverId driverId,
        AccessGrantReason reason,
        List<String> permissions,  // 授予的权限列表，如 "REMOTE_VIDEO", "REMOTE_VOICE"
        Instant grantedTime
) implements DomainEvent {

    public FamilyAccessGrantedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(driverId, "driverId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(permissions, "permissions must not be null");
        Objects.requireNonNull(grantedTime, "grantedTime must not be null");
    }

    public FamilyAccessGrantedEvent(AccountId accountId, DriverId driverId,
                                     AccessGrantReason reason, List<String> permissions,
                                     Instant grantedTime) {
        this(
                java.util.UUID.randomUUID().toString(),
                Instant.now(),
                new AggregateId(accountId.id(), AggregateType.SYSTEM_ACCOUNT),
                accountId,
                driverId,
                reason,
                permissions,
                grantedTime
        );
    }
}
