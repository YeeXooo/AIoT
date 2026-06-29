package com.aiot.domain.shared;

public record RescueReportId(String id) {
    public static RescueReportId generate() { return new RescueReportId(AggregateId.generate().id()); }
}
