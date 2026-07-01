package com.aiot.domain.model;

import com.aiot.domain.shared.DriverId;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import java.time.Instant;

/**
 * 驾驶员状态快照（VO-15）
 * 家属端常态同步的轻量周期快照，对应绿/黄/红三色状态
 */
@Embeddable
@Getter
@EqualsAndHashCode
public final class DriverStatusSnapshot {
    private final DriverId driverId;
    private final StatusColor statusColor;
    private final Instant timestamp;

    private DriverStatusSnapshot(DriverId driverId, StatusColor statusColor, Instant timestamp) {
        if (driverId == null) throw new IllegalArgumentException("驾驶员标识不能为空");
        if (statusColor == null) throw new IllegalArgumentException("状态色不能为空");
        if (timestamp == null) throw new IllegalArgumentException("时间戳不能为空");
        this.driverId = driverId;
        this.statusColor = statusColor;
        this.timestamp = timestamp;
    }

    public static DriverStatusSnapshot of(DriverId driverId, StatusColor statusColor, Instant timestamp) {
        return new DriverStatusSnapshot(driverId, statusColor, timestamp);
    }

    protected DriverStatusSnapshot() {
        this.driverId = null;
        this.statusColor = null;
        this.timestamp = Instant.EPOCH;
    }
}