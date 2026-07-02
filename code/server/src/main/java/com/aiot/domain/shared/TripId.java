package com.aiot.domain.shared;
import org.springframework.stereotype.Service;

public record TripId(String id) {
    public static TripId generate() { return new TripId(AggregateId.generate(AggregateType.TRIP).value()); }
}
