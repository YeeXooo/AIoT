package com.aiot.domain.shared;
import org.springframework.stereotype.Service;

public record VehicleId(String id) {
    public static VehicleId generate() { return new VehicleId(AggregateId.generate(AggregateType.VEHICLE).value()); }
}
