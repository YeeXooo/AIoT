package com.aiot.domain.port;

import java.time.Instant;

/**
 * 驾驶行为追踪端口。
 * <p>
 * 基础设施层在检测到急刹/急加速事件时，以回调方式通知领域层。
 * 阈值由基础设施层按车型/传感器标定配置。
 * </p>
 * <p>
 * 设计依据：docs/ood_domain.md 决策 17、决策 20
 * </p>
 */
public interface DrivingBehaviorTrackingPort {

    /**
     * 急刹事件回调。
     *
     * @param event 急刹事件
     */
    void onHardBrakingDetected(HardBrakingEvent event);

    /**
     * 急加速事件回调。
     *
     * @param event 急加速事件
     */
    void onHardAccelerationDetected(HardAccelerationEvent event);

    /**
     * 急刹事件。
     *
     * @param timestamp     事件发生时间
     * @param deceleration  减速度值（m/s²）
     */
    record HardBrakingEvent(Instant timestamp, double deceleration) {
        public HardBrakingEvent {
            if (deceleration <= 0) {
                throw new IllegalArgumentException("Deceleration must be positive");
            }
        }
    }

    /**
     * 急加速事件。
     *
     * @param timestamp    事件发生时间
     * @param acceleration 加速度值（m/s²）
     */
    record HardAccelerationEvent(Instant timestamp, double acceleration) {
        public HardAccelerationEvent {
            if (acceleration <= 0) {
                throw new IllegalArgumentException("Acceleration must be positive");
            }
        }
    }
}
