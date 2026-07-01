package com.aiot.infra.adapter;

import com.aiot.domain.port.CameraOcclusionDetectionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 摄像头遮挡检测适配器。
 * <p>
 * 本期以模拟数据源直接注入遮挡/移除信号。
 * </p>
 * <p>
 * 设计依据：docs/ood_infrastructure.md §3.4.4
 * </p>
 */
@Component
public class CameraOcclusionDetectionAdapter implements CameraOcclusionDetectionPort {

    private static final Logger log = LoggerFactory.getLogger(CameraOcclusionDetectionAdapter.class);

    private CameraOcclusionCallback callback;
    private Instant lastOcclusionDetectedAt;

    /**
     * 设置回调处理器。
     */
    public void setCallback(CameraOcclusionCallback callback) {
        this.callback = callback;
    }

    @Override
    public void onOcclusionDetected(OcclusionDetectedSignal event) {
        lastOcclusionDetectedAt = event.timestamp();
        log.info("Camera occlusion detected: sensor={}, type={} at {}",
                event.sensorId(), event.occlusionType(), event.timestamp());
        if (callback != null) {
            callback.onOcclusionDetected(event);
        }
    }

    @Override
    public void onOcclusionRemoved(OcclusionRemovedSignal event) {
        Long duration = event.durationMillis();
        if (duration == null && lastOcclusionDetectedAt != null) {
            duration = Instant.now().toEpochMilli() - lastOcclusionDetectedAt.toEpochMilli();
        }
        log.info("Camera occlusion removed: sensor={}, duration={}ms at {}",
                event.sensorId(), duration, event.timestamp());
        lastOcclusionDetectedAt = null;
        if (callback != null) {
            callback.onOcclusionRemoved(event);
        }
    }

    /**
     * 模拟遮挡检测（用于测试）。
     */
    public void simulateOcclusionDetected(String sensorId, OcclusionType type) {
        OcclusionDetectedSignal signal = new OcclusionDetectedSignal(
                Instant.now(), sensorId, type);
        onOcclusionDetected(signal);
    }

    /**
     * 模拟遮挡移除（用于测试）。
     */
    public void simulateOcclusionRemoved(String sensorId) {
        OcclusionRemovedSignal signal = new OcclusionRemovedSignal(
                Instant.now(), sensorId, null);
        onOcclusionRemoved(signal);
    }

    /**
     * 回调接口。
     */
    public interface CameraOcclusionCallback {
        void onOcclusionDetected(OcclusionDetectedSignal event);
        void onOcclusionRemoved(OcclusionRemovedSignal event);
    }
}
