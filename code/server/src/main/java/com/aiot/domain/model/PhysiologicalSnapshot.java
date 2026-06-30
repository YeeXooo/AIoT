package com.aiot.domain.model;

import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import java.time.Instant;

/**
 * 生理体征快照（VO-03）
 * 传感器固定频率采集的瞬时生理数据，不可变时间点切片
 * 被 Trip 聚合根持有为集合，仅追加不修改
 */
@Embeddable
@Getter
@EqualsAndHashCode
public final class PhysiologicalSnapshot {
    private final Instant timestamp;
    private final int heartRate;
    private final int bloodOxygen;
    private final double emotionIndex;

    private PhysiologicalSnapshot(Instant timestamp, int heartRate, int bloodOxygen, double emotionIndex) {
        if (timestamp == null) throw new IllegalArgumentException("采集时间戳不能为空");
        if (heartRate <= 0 || heartRate > 250) throw new IllegalArgumentException("心率数值不合法");
        if (bloodOxygen < 0 || bloodOxygen > 100) throw new IllegalArgumentException("血氧数值不合法");
        this.timestamp = timestamp;
        this.heartRate = heartRate;
        this.bloodOxygen = bloodOxygen;
        this.emotionIndex = emotionIndex;
    }

    public static PhysiologicalSnapshot of(Instant timestamp, int heartRate, int bloodOxygen, double emotionIndex) {
        return new PhysiologicalSnapshot(timestamp, heartRate, bloodOxygen, emotionIndex);
    }

    // JPA 反射所需保护空构造器
    protected PhysiologicalSnapshot() {
        this.timestamp = Instant.EPOCH;
        this.heartRate = 0;
        this.bloodOxygen = 0;
        this.emotionIndex = 0;
    }
}