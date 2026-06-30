package com.aiot.domain.shared;

public record DriverId(String id) {
    public static DriverId generate() { return new DriverId(AggregateId.generate(AggregateType.DRIVER).value()); }
}
