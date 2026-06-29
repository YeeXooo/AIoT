package com.aiot.domain.shared;

public record TripId(String id) {
    public static TripId generate() { return new TripId(AggregateId.generate().id()); }
}
