package com.aiot.domain.shared;

import java.util.Objects;
import java.util.UUID;

public record AggregateId(String id) {

    public AggregateId {
        Objects.requireNonNull(id, "id must not be null");
    }

    public static AggregateId generate() {
        return new AggregateId(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return id;
    }
}
