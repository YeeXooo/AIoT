package com.aiot.interfaces.websocket;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * WebSocket 配置。
 * <p>
 * 设计依据：docs/ood_interface.md §3、docs/ood_infrastructure.md §3.7.5
 * </p>
 */
@Component
@ConfigurationProperties(prefix = "aiot.websocket")
public class WebSocketProperties {

    /** 心跳间隔（秒） */
    private int heartbeatIntervalSec = 30;

    /** 心跳超时（秒），超过此时长未收到 pong 视为断线 */
    private int heartbeatTimeoutSec = 10;

    /** 最大连续丢失心跳数，超限主动断开 */
    private int maxMissedHeartbeats = 3;

    /** 每驾驶员最大家属订阅数 */
    private int maxSubscriptionsPerDriver = 3;

    /** 离线告警补推上限 */
    private int maxOfflineAlertsPerReconnect = 20;

    /** 离线消息保留天数 */
    private int offlineMessageRetentionDays = 7;

    /** 家属 WebSocket 端点路径 */
    private String guardianshipEndpoint = "/ws/guardianship";

    /** 车队大屏 WebSocket 端点路径 */
    private String fleetEndpoint = "/ws/fleet";

    // ── getters / setters ──

    public int getHeartbeatIntervalSec() { return heartbeatIntervalSec; }
    public void setHeartbeatIntervalSec(int heartbeatIntervalSec) { this.heartbeatIntervalSec = heartbeatIntervalSec; }

    public int getHeartbeatTimeoutSec() { return heartbeatTimeoutSec; }
    public void setHeartbeatTimeoutSec(int heartbeatTimeoutSec) { this.heartbeatTimeoutSec = heartbeatTimeoutSec; }

    public int getMaxMissedHeartbeats() { return maxMissedHeartbeats; }
    public void setMaxMissedHeartbeats(int maxMissedHeartbeats) { this.maxMissedHeartbeats = maxMissedHeartbeats; }

    public int getMaxSubscriptionsPerDriver() { return maxSubscriptionsPerDriver; }
    public void setMaxSubscriptionsPerDriver(int maxSubscriptionsPerDriver) { this.maxSubscriptionsPerDriver = maxSubscriptionsPerDriver; }

    public int getMaxOfflineAlertsPerReconnect() { return maxOfflineAlertsPerReconnect; }
    public void setMaxOfflineAlertsPerReconnect(int maxOfflineAlertsPerReconnect) { this.maxOfflineAlertsPerReconnect = maxOfflineAlertsPerReconnect; }

    public int getOfflineMessageRetentionDays() { return offlineMessageRetentionDays; }
    public void setOfflineMessageRetentionDays(int offlineMessageRetentionDays) { this.offlineMessageRetentionDays = offlineMessageRetentionDays; }

    public String getGuardianshipEndpoint() { return guardianshipEndpoint; }
    public void setGuardianshipEndpoint(String guardianshipEndpoint) { this.guardianshipEndpoint = guardianshipEndpoint; }

    public String getFleetEndpoint() { return fleetEndpoint; }
    public void setFleetEndpoint(String fleetEndpoint) { this.fleetEndpoint = fleetEndpoint; }
}
