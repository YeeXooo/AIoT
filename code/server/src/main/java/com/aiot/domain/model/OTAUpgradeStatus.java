package com.aiot.domain.model;

import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import java.time.Instant;

/**
 * OTA 升级状态（VO-19）
 * 升级会话状态机，断点续传与阶段超时判定依据
 */
@Embeddable
@Getter
@EqualsAndHashCode
public final class OTAUpgradeStatus {
    private final UpgradeStage stage;
    private final OTAVersion targetVersion;
    private final long offset;
    private final Instant stageTimestamp;

    private OTAUpgradeStatus(UpgradeStage stage, OTAVersion targetVersion, long offset, Instant stageTimestamp) {
        if (stage == null) throw new IllegalArgumentException("升级阶段不能为空");
        if (offset < 0) throw new IllegalArgumentException("偏移量不能为负");
        this.stage = stage;
        this.targetVersion = targetVersion;
        this.offset = offset;
        this.stageTimestamp = stageTimestamp;
    }

    public static OTAUpgradeStatus init(OTAVersion targetVersion, Instant startTime) {
        return new OTAUpgradeStatus(UpgradeStage.PENDING, targetVersion, 0, startTime);
    }

    public OTAUpgradeStatus transition(UpgradeStage nextStage, long newOffset, Instant now) {
        return new OTAUpgradeStatus(nextStage, this.targetVersion, newOffset, now);
    }

    protected OTAUpgradeStatus() {
        this.stage = null;
        this.targetVersion = null;
        this.offset = 0;
        this.stageTimestamp = Instant.EPOCH;
    }
}