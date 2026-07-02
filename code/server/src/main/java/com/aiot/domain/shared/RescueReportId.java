package com.aiot.domain.shared;

import java.util.UUID;
import org.springframework.stereotype.Service;

public record RescueReportId(String id) {
    public static RescueReportId generate() { return new RescueReportId(UUID.randomUUID().toString()); }
}
