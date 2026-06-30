package com.aiot.domain.model;

import com.aiot.domain.model.exception.BusinessException;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * 传感器读数（VO-11）
 * 五大感知通道的统一数据抽象，为判定服务提供标准输入契约
 */
@Embeddable
@Getter
@EqualsAndHashCode
public final class SensorReading {
    private final String channelType;
    private final Instant timestamp;
    private final String rawPayloadRef;
    private final List<Double> featureVector;

    private SensorReading(String channelType, Instant timestamp, String rawPayloadRef, List<Double> featureVector) {
        if (channelType == null || channelType.isBlank()) {
            throw new BusinessException(
                    "MODEL_028",
                    "传感器通道类型不能为空",
                    "SENSOR_READING_VALIDATE"
            );
        }
        if (timestamp == null) {
            throw new BusinessException(
                    "MODEL_029",
                    "传感器读数时间戳不能为空",
                    "SENSOR_READING_VALIDATE"
            );
        }
        this.channelType = channelType;
        this.timestamp = timestamp;
        this.rawPayloadRef = rawPayloadRef;
        this.featureVector = featureVector == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(featureVector);
    }

    public static SensorReading of(String channelType, Instant timestamp,
                                   String rawPayloadRef, List<Double> featureVector) {
        return new SensorReading(channelType, timestamp, rawPayloadRef, featureVector);
    }

    protected SensorReading() {
        this.channelType = "";
        this.timestamp = Instant.EPOCH;
        this.rawPayloadRef = "";
        this.featureVector = Collections.emptyList();
    }
}