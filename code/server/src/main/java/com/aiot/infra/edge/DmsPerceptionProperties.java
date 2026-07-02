package com.aiot.infra.edge;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DMS 感知源配置。
 * <p>
 * 控制 DMS CAMERA 通道的感知实现选择：
 * mock → 既有 Java 模拟源（测试/无摄像头环境）
 * yolo → Python sidecar + DmsPerceptionAdapter（真实视觉感知）
 * </p>
 * <p>
 * 设计依据：docs/ood_perception_yolo.md §6.1
 * </p>
 */
@Component
@ConfigurationProperties(prefix = "aiot.perception.dms")
public class DmsPerceptionProperties {

    /**
     * 感知模式：mock | yolo
     */
    private String mode = "mock";

    /**
     * gRPC sidecar 服务地址（仅 yolo 模式）
     */
    private String grpcHost = "localhost";

    /**
     * gRPC sidecar 服务端口（仅 yolo 模式）
     */
    private int grpcPort = 50051;

    /**
     * 传感器标识
     */
    private String sensorId = "dms_camera_0";

    /**
     * 目标推理帧率
     */
    private double targetFps = 10.0;

    /**
     * 推理超时（毫秒），超时则丢弃该帧
     */
    private long frameTimeoutMs = 200;

    /**
     * 健康检查间隔（秒）
     */
    private int healthCheckIntervalSec = 5;

    // ── JavaBean getters / setters (Spring Boot @ConfigurationProperties binding) ──

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getGrpcHost() { return grpcHost; }
    public void setGrpcHost(String grpcHost) { this.grpcHost = grpcHost; }

    public int getGrpcPort() { return grpcPort; }
    public void setGrpcPort(int grpcPort) { this.grpcPort = grpcPort; }

    public String getSensorId() { return sensorId; }
    public void setSensorId(String sensorId) { this.sensorId = sensorId; }

    public double getTargetFps() { return targetFps; }
    public void setTargetFps(double targetFps) { this.targetFps = targetFps; }

    public long getFrameTimeoutMs() { return frameTimeoutMs; }
    public void setFrameTimeoutMs(long frameTimeoutMs) { this.frameTimeoutMs = frameTimeoutMs; }

    public int getHealthCheckIntervalSec() { return healthCheckIntervalSec; }
    public void setHealthCheckIntervalSec(int healthCheckIntervalSec) { this.healthCheckIntervalSec = healthCheckIntervalSec; }

    public boolean isYoloMode() {
        return "yolo".equalsIgnoreCase(mode);
    }
}
