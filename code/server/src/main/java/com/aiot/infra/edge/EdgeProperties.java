package com.aiot.infra.edge;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 边缘侧基础设施配置。
 * <p>
 * 控制边缘侧本地持久化、离线缓冲、MQTT 连接和云端同步行为。
 * 通过 {@code aiot.edge.mode} 切换运行模式：{@code cloud} 仅在云端运行，
 * {@code edge} 启用边缘侧全部能力。
 * </p>
 * <p>
 * 设计依据：docs/ood_infrastructure.md §3.7、docs/ood_infrastructure.md §3.8
 * </p>
 */
@Component
@ConfigurationProperties(prefix = "aiot.edge")
public class EdgeProperties {

    /** 运行模式：cloud（仅云端）/ edge（边缘侧） */
    private String mode = "cloud";

    /** 边缘设备唯一标识（序列号） */
    private String deviceId = "";

    /** MQTT Broker 地址（边缘侧连接 IoTDA） */
    private String brokerUrl = "tcp://localhost:1883";

    /** MQTT 客户端 ID 前缀 */
    private String clientIdPrefix = "aiot-edge";

    /** MQTT 用户名 */
    private String mqttUsername = "";

    /** MQTT 密码 */
    private String mqttPassword = "";

    /** 离线缓冲最大条目数 */
    private int bufferMaxEntries = 10000;

    /** 离线缓冲保留小时数 */
    private int bufferRetentionHours = 24;

    /** 重传批量大小 */
    private int batchSize = 100;

    /** 重传间隔（秒） */
    private int retryIntervalSec = 30;

    /** 最大重试次数 */
    private int maxRetries = 5;

    /** SQLite 数据库文件路径 */
    private String sqlitePath = "/data/aiot/edge.db";

    /** 云端 API 基础 URL（用于 HTTP 同步回退） */
    private String cloudApiUrl = "http://localhost:8080";

    // ── getters / setters ──

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getBrokerUrl() { return brokerUrl; }
    public void setBrokerUrl(String brokerUrl) { this.brokerUrl = brokerUrl; }

    public String getClientIdPrefix() { return clientIdPrefix; }
    public void setClientIdPrefix(String clientIdPrefix) { this.clientIdPrefix = clientIdPrefix; }

    public String getMqttUsername() { return mqttUsername; }
    public void setMqttUsername(String mqttUsername) { this.mqttUsername = mqttUsername; }

    public String getMqttPassword() { return mqttPassword; }
    public void setMqttPassword(String mqttPassword) { this.mqttPassword = mqttPassword; }

    public int getBufferMaxEntries() { return bufferMaxEntries; }
    public void setBufferMaxEntries(int bufferMaxEntries) { this.bufferMaxEntries = bufferMaxEntries; }

    public int getBufferRetentionHours() { return bufferRetentionHours; }
    public void setBufferRetentionHours(int bufferRetentionHours) { this.bufferRetentionHours = bufferRetentionHours; }

    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

    public int getRetryIntervalSec() { return retryIntervalSec; }
    public void setRetryIntervalSec(int retryIntervalSec) { this.retryIntervalSec = retryIntervalSec; }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

    public String getSqlitePath() { return sqlitePath; }
    public void setSqlitePath(String sqlitePath) { this.sqlitePath = sqlitePath; }

    public String getCloudApiUrl() { return cloudApiUrl; }
    public void setCloudApiUrl(String cloudApiUrl) { this.cloudApiUrl = cloudApiUrl; }

    /** 是否为边缘模式 */
    public boolean isEdgeMode() {
        return "edge".equalsIgnoreCase(mode);
    }
}
