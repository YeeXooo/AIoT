package com.aiot.domain.model;

import com.aiot.domain.model.exception.BusinessException;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import java.time.Instant;

/**
 * 生理体征快照（VO-03）
 * 传感器固定频率采集的瞬时生理数据，不可变时间点切片
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
        if (timestamp == null) {
            throw new BusinessException(
                    "MODEL_017",
                    "生理快照采集时间戳不能为空",
                    "PHYSIOLOGICAL_SNAPSHOT_VALIDATE"
            );
        }
        if (heartRate <= 0 || heartRate > 250) {
            throw new BusinessException(
                    "MODEL_018",
                    String.format("心率数值不合法，当前值：%d，合法范围(0,250]", heartRate),
                    "PHYSIOLOGICAL_SNAPSHOT_VALIDATE"
            );
        }
        if (bloodOxygen < 0 || bloodOxygen > 100) {
            throw new BusinessException(
                    "MODEL_019",
                    String.format("血氧数值不合法，当前值：%d，合法范围[0,100]", bloodOxygen),
                    "PHYSIOLOGICAL_SNAPSHOT_VALIDATE"
            );
        }
        this.timestamp = timestamp;
        this.heartRate = heartRate;
        this.bloodOxygen = bloodOxygen;
        this.emotionIndex = emotionIndex;
    }

    public static PhysiologicalSnapshot of(Instant timestamp, int heartRate, int bloodOxygen, double emotionIndex) {
        return new PhysiologicalSnapshot(timestamp, heartRate, bloodOxygen, emotionIndex);
    }

    protected PhysiologicalSnapshot() {
        this.timestamp = Instant.EPOCH;
        this.heartRate = 0;
        this.bloodOxygen = 0;
        this.emotionIndex = 0;
    }
}