package com.aiot.domain.shared;

public record VehicleId(String id) {
    public static VehicleId generate() { return new VehicleId(AggregateId.generate().id()); }
}
