package com.aiot.domain.shared;

public record UpgradeTaskId(String id) {
    public static UpgradeTaskId generate() { return new UpgradeTaskId(AggregateId.generate().id()); }
}
