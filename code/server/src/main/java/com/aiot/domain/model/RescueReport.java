package com.aiot.domain.model;

import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import java.util.Collections;
import java.util.List;

/**
 * 救援报告（VO-13）
 * SOS 上报时的完整信息聚合载体，一次性定格不可修改
 */
@Embeddable
@Getter
@EqualsAndHashCode
public final class RescueReport {
    private final GeoLocation location;
    private final String vitalSignsSummary;
    private final List<VehicleStateSnapshot> vehicleStateSnapshots;
    private final String healthProfileSummary;

    private RescueReport(GeoLocation location, String vitalSignsSummary,
                         List<VehicleStateSnapshot> snapshots, String healthProfileSummary) {
        if (location == null) throw new IllegalArgumentException("定位信息不能为空");
        this.location = location;
        this.vitalSignsSummary = vitalSignsSummary;
        this.vehicleStateSnapshots = snapshots == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(snapshots);
        this.healthProfileSummary = healthProfileSummary;
    }

    public static RescueReport of(GeoLocation location, String vitalSignsSummary,
                                  List<VehicleStateSnapshot> snapshots, String healthProfileSummary) {
        return new RescueReport(location, vitalSignsSummary, snapshots, healthProfileSummary);
    }

    protected RescueReport() {
        this.location = null;
        this.vitalSignsSummary = "";
        this.vehicleStateSnapshots = Collections.emptyList();
        this.healthProfileSummary = "";
    }
}