package com.aiot.domain.shared;

public record AccountId(String id) {
    public static AccountId generate() { return new AccountId(AggregateId.generate(AggregateType.SYSTEM_ACCOUNT).value()); }
}
