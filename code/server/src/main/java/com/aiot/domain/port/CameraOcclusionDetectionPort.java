package com.aiot.domain.port;

import java.time.Instant;

/**
 * 摄像头遮挡检测端口。
 * <p>
 * 基础设施层感知通道检测到摄像头物理遮挡时回调通知领域层。
 * 区别于传感器故障（SensorFailureEvent），遮挡不影响传感器健康状态。
 * </p>
 * <p>
 * 设计依据：docs/ood_domain.md DS-14
 * </p>
 */
public interface CameraOcclusionDetectionPort {

    /**
     * 遮挡检测回调。
     *
     * @param event 遮挡检测信号
     */
    void onOcclusionDetected(OcclusionDetectedSignal event);

    /**
     * 遮挡移除回调。
     *
     * @param event 遮挡移除信号
     */
    void onOcclusionRemoved(OcclusionRemovedSignal event);

    /**
     * 遮挡检测信号。
     *
     * @param timestamp      检测到遮挡的时刻
     * @param sensorId       被遮挡的传感器标识
     * @param occlusionType  遮挡类型（可选）
     */
    record OcclusionDetectedSignal(
            Instant timestamp,
            String sensorId,
            OcclusionType occlusionType
    ) { }

    /**
     * 遮挡移除信号。
     *
     * @param timestamp        遮挡移除的时刻
     * @param sensorId         恢复的传感器标识
     * @param durationMillis   遮挡持续时间（毫秒）
     */
    record OcclusionRemovedSignal(
            Instant timestamp,
            String sensorId,
            Long durationMillis
    ) { }

    /**
     * 遮挡类型枚举。
     */
    enum OcclusionType {
        PHYSICAL_COVER,  // 物体遮盖
        ADHESIVE,        // 贴纸/污渍
        UNKNOWN          // 未能识别的遮挡模式
    }
}
