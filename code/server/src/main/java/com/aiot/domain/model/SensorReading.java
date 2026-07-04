package com.aiot.domain.model;

import com.aiot.domain.shared.TripId;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 传感器读数值对象。
 * <p>
 * 五大感知通道的统一感知数据抽象，封装通道类型、采集时间戳和已提取的特征向量。
 * 为各判定服务提供统一的输入契约。
 * </p>
 * <p>
 * 设计依据：docs/ood_domain.md VO-11
 * DMS_CAMERA 字段约定详见 docs/ood_perception_yolo.md §2.1
 * </p>
 */
public class SensorReading {

    public enum SensorType {
        DMS_CAMERA,
        MILLIMETER_WAVE_RADAR,
        ACCELEROMETER,
        PHYSIOLOGICAL_MONITOR,
        MICROPHONE,
        ACOUSTIC,
        REAR_IR_CAMERA,
        ENVIRONMENT
    }

    private final SensorType sensorType;
    private final Instant timestamp;
    private final TripId tripId;
    private final Map<String, Double> values;

    public SensorReading(SensorType sensorType, Instant timestamp,
                         TripId tripId, Map<String, Double> values) {
        this.sensorType = Objects.requireNonNull(sensorType, "sensorType must not be null");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        this.tripId = Objects.requireNonNull(tripId, "tripId must not be null");
        this.values = Collections.unmodifiableMap(
                new HashMap<>(Objects.requireNonNull(values, "values must not be null"))
        );
    }

    public SensorType sensorType() { return sensorType; }
    public Instant timestamp() { return timestamp; }
    public TripId tripId() { return tripId; }
    public Map<String, Double> values() { return values; }

    /** 便捷取值，不存在时返回 0。 */
    public double get(String key) {
        return values.getOrDefault(key, 0.0);
    }

    // ── DMS_CAMERA 通道字段便捷方法 ──

    public double perclos()          { return get("PERCLOS"); }
    public double yawnFreq()         { return get("yawnFreq"); }
    public double headNodFreq()      { return get("headNodFreq"); }
    public double gazeDeviationCumulative() { return get("gazeDeviationCumulative"); }
    public double handsOffWheel()    { return get("handsOffWheel"); }

    // ── ENVIRONMENT 通道字段便捷方法 ──

    public double temp() { return get("TEMP"); }
    public double humi() { return get("HUMI"); }
    public double lux()  { return get("LUX"); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SensorReading that)) return false;
        return sensorType == that.sensorType
                && timestamp.equals(that.timestamp)
                && tripId.equals(that.tripId)
                && values.equals(that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sensorType, timestamp, tripId, values);
    }

    @Override
    public String toString() {
        return "SensorReading{" + sensorType + ", " + timestamp + ", fields=" + values.size() + "}";
    }
}
