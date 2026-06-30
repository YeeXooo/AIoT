package com.aiot.domain.model;

import com.aiot.domain.shared.DriverId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 救援报告值对象。
 * <p>
 * EmergencyRescueService 在 SOS 上报时产出的救援信息聚合载体。
 * </p>
 * <p>
 * 设计依据：docs/ood_domain.md VO-13
 * </p>
 */
public record RescueReport(
        DriverId driverId,
        GeoLocation location,
        PhysiologicalSnapshot latestVitals,
        List<VehicleStateSnapshot> vehicleStates,
        String healthSummary,
        Instant occurredAt
) {
    public RescueReport {
        Objects.requireNonNull(driverId, "driverId must not be null");
        Objects.requireNonNull(location, "location must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
