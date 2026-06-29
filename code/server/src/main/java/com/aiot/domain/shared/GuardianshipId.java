package com.aiot.domain.shared;

public record GuardianshipId(String id) {
    public static GuardianshipId generate() { return new GuardianshipId(AggregateId.generate().id()); }
}
