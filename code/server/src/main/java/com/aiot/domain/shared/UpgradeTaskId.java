package com.aiot.domain.shared;

import java.util.UUID;
import org.springframework.stereotype.Service;

public record UpgradeTaskId(String id) {
    public static UpgradeTaskId generate() { return new UpgradeTaskId(UUID.randomUUID().toString()); }
}
