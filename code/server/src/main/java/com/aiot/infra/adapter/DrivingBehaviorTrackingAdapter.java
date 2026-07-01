package com.aiot.infra.adapter;

import com.aiot.domain.port.DrivingBehaviorTrackingPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 驾驶行为追踪适配器。
 * <p>
 * 模拟数据源，按固定频率检测急刹/急加速事件并回调领域层。
 * 验收测试时以固定阈值（减速度 > 3.5 m/s²，加速度 > 3.0 m/s²）注入。
 * </p>
 * <p>
 * 设计依据：docs/ood_infrastructure.md §3.4.3
 * </p>
 */
@Component
public class DrivingBehaviorTrackingAdapter implements DrivingBehaviorTrackingPort {

    private static final Logger log = LoggerFactory.getLogger(DrivingBehaviorTrackingAdapter.class);

    /**
     * 急刹阈值（m/s²）
     */
    private final double hardBrakingThreshold;

    /**
     * 急加速阈值（m/s²）
     */
    private final double hardAccelerationThreshold;

    private DrivingBehaviorTrackingCallback callback;

    public DrivingBehaviorTrackingAdapter() {
        this(3.5, 3.0);
    }

    public DrivingBehaviorTrackingAdapter(double hardBrakingThreshold, double hardAccelerationThreshold) {
        this.hardBrakingThreshold = hardBrakingThreshold;
        this.hardAccelerationThreshold = hardAccelerationThreshold;
    }

    /**
     * 设置回调处理器。
     */
    public void setCallback(DrivingBehaviorTrackingCallback callback) {
        this.callback = callback;
    }

    @Override
    public void onHardBrakingDetected(HardBrakingEvent event) {
        log.info("Hard braking detected: deceleration={} m/s² at {}", event.deceleration(), event.timestamp());
        if (callback != null) {
            callback.onHardBraking(event);
        }
    }

    @Override
    public void onHardAccelerationDetected(HardAccelerationEvent event) {
        log.info("Hard acceleration detected: acceleration={} m/s² at {}", event.acceleration(), event.timestamp());
        if (callback != null) {
            callback.onHardAcceleration(event);
        }
    }

    /**
     * 模拟急刹事件（用于测试）。
     */
    public void simulateHardBraking(double deceleration) {
        if (deceleration >= hardBrakingThreshold) {
            HardBrakingEvent event = new HardBrakingEvent(Instant.now(), deceleration);
            onHardBrakingDetected(event);
        }
    }

    /**
     * 模拟急加速事件（用于测试）。
     */
    public void simulateHardAcceleration(double acceleration) {
        if (acceleration >= hardAccelerationThreshold) {
            HardAccelerationEvent event = new HardAccelerationEvent(Instant.now(), acceleration);
            onHardAccelerationDetected(event);
        }
    }

    /**
     * 回调接口。
     */
    public interface DrivingBehaviorTrackingCallback {
        void onHardBraking(HardBrakingEvent event);
        void onHardAcceleration(HardAccelerationEvent event);
    }
}
