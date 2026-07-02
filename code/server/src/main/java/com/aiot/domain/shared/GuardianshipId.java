package com.aiot.domain.shared;

import java.util.UUID;
import org.springframework.stereotype.Service;

public record GuardianshipId(String id) {
    public static GuardianshipId generate() { return new GuardianshipId(UUID.randomUUID().toString()); }
}
