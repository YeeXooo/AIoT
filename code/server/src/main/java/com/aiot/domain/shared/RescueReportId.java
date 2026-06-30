package com.aiot.domain.shared;

import java.util.UUID;

public record RescueReportId(String id) {
    public static RescueReportId generate() { return new RescueReportId(UUID.randomUUID().toString()); }
}
