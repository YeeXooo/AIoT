package com.aiot.domain.shared;

public record AlertId(String id) {
    public static AlertId generate() { return new AlertId(AggregateId.generate().id()); }
}
