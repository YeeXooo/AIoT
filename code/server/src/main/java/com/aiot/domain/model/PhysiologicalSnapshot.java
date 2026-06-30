package com.aiot.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 生理体征快照值对象。
 * <p>
 * 传感器按固定频率采集的瞬时生理数据。
 * </p>
 * <p>
 * 设计依据：docs/ood_domain.md VO-03
 * </p>
 */
public record PhysiologicalSnapshot(
        Instant timestamp,
        Integer heartRate,
        Double bloodOxygen,
        Double emotionIndex,
        Integer respiratoryRate,
        Integer systolicBp,
        Integer diastolicBp,
        Double fatigueIndex,
        Double bodyTemperature
) {
    public PhysiologicalSnapshot {
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }
}
