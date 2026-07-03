package com.aiot.interfaces.mqtt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MQTT 连接与 IoTDA 设备认证配置。
 * <p>
 * 支持标准 MQTT Broker 和华为云 IoTDA 两种连接模式，
 * 通过 {@code iotda.enabled} 切换。
 * </p>
 * <p>
 * 设计依据：docs/ood_interface.md §2、docs/ood_interface.md §5.4
 * </p>
 */
@Component
@ConfigurationProperties(prefix = "aiot.mqtt")
public class MqttProperties {

    /** MQTT Broker 地址 */
    private String brokerUrl = "tcp://localhost:1883";

    /** 客户端 ID 前缀 */
    private String clientIdPrefix = "aiot-cloud";

    /** 用户名 */
    private String username = "";

    /** 密码 */
    private String password = "";

    /** 连接超时（秒） */
    private int connectionTimeoutSec = 30;

    /** 心跳间隔（秒） */
    private int keepAliveIntervalSec = 60;

    /** 默认 QoS */
    private int defaultQos = 1;

    /** 是否启用真实 MQTT 连接（false 时使用日志模拟） */
    private boolean enabled = false;

    /** Topic 前缀（IoTDA 产品级前缀） */
    private String topicPrefix = "";

    // ── IoTDA 专用配置 ──

    /** IoTDA 模式（启用后使用设备 Token 认证） */
    private boolean iotda = false;

    /** IoTDA 设备认证 Token（用于设备级 MQTT 连接认证） */
    private String deviceSecret = "";

    /** IoTDA 产品 ID */
    private String productId = "";

    /** IoTDA 实例 ID */
    private String instanceId = "";

    /** 设备 Token 有效期（秒），默认 24h */
    private int deviceTokenTtlSec = 86400;

    /** MQTT over TLS: CA 证书路径（IoTDA 要求 TLS） */
    private String tlsCaCertPath = "";

    /** MQTT over TLS: 客户端证书路径 */
    private String tlsClientCertPath = "";

    /** MQTT over TLS: 客户端私钥路径 */
    private String tlsClientKeyPath = "";

    // ── 上行主题订阅配置 ──

    /** 是否订阅设备上行消息 */
    private boolean subscribeUplink = true;

    /** 通配订阅所有设备上行数据 */
    private boolean wildcardSubscription = true;

    /** 上行主题过滤（通配订阅时生效，默认订阅所有产品设备） */
    private String uplinkTopicFilter = "#";

    // ── getters / setters ──

    public String getBrokerUrl() { return brokerUrl; }
    public void setBrokerUrl(String brokerUrl) { this.brokerUrl = brokerUrl; }

    public String getClientIdPrefix() { return clientIdPrefix; }
    public void setClientIdPrefix(String clientIdPrefix) { this.clientIdPrefix = clientIdPrefix; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public int getConnectionTimeoutSec() { return connectionTimeoutSec; }
    public void setConnectionTimeoutSec(int connectionTimeoutSec) { this.connectionTimeoutSec = connectionTimeoutSec; }

    public int getKeepAliveIntervalSec() { return keepAliveIntervalSec; }
    public void setKeepAliveIntervalSec(int keepAliveIntervalSec) { this.keepAliveIntervalSec = keepAliveIntervalSec; }

    public int getDefaultQos() { return defaultQos; }
    public void setDefaultQos(int defaultQos) { this.defaultQos = defaultQos; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getTopicPrefix() { return topicPrefix; }
    public void setTopicPrefix(String topicPrefix) { this.topicPrefix = topicPrefix; }

    public boolean isIotda() { return iotda; }
    public void setIotda(boolean iotda) { this.iotda = iotda; }

    public String getDeviceSecret() { return deviceSecret; }
    public void setDeviceSecret(String deviceSecret) { this.deviceSecret = deviceSecret; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }

    public int getDeviceTokenTtlSec() { return deviceTokenTtlSec; }
    public void setDeviceTokenTtlSec(int deviceTokenTtlSec) { this.deviceTokenTtlSec = deviceTokenTtlSec; }

    public String getTlsCaCertPath() { return tlsCaCertPath; }
    public void setTlsCaCertPath(String tlsCaCertPath) { this.tlsCaCertPath = tlsCaCertPath; }

    public String getTlsClientCertPath() { return tlsClientCertPath; }
    public void setTlsClientCertPath(String tlsClientCertPath) { this.tlsClientCertPath = tlsClientCertPath; }

    public String getTlsClientKeyPath() { return tlsClientKeyPath; }
    public void setTlsClientKeyPath(String tlsClientKeyPath) { this.tlsClientKeyPath = tlsClientKeyPath; }

    public boolean isSubscribeUplink() { return subscribeUplink; }
    public void setSubscribeUplink(boolean subscribeUplink) { this.subscribeUplink = subscribeUplink; }

    public boolean isWildcardSubscription() { return wildcardSubscription; }
    public void setWildcardSubscription(boolean wildcardSubscription) { this.wildcardSubscription = wildcardSubscription; }

    public String getUplinkTopicFilter() { return uplinkTopicFilter; }
    public void setUplinkTopicFilter(String uplinkTopicFilter) { this.uplinkTopicFilter = uplinkTopicFilter; }
}
