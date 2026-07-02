package com.aiot.infra.edge;

import org.eclipse.paho.mqttv5.client.IMqttAsyncClient;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.util.MqttTopicValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 边缘侧 MQTT 客户端。
 * <p>
 * 在边缘设备（车载终端）上运行，连接云端 IoTDA Broker，
 * 上报传感器数据、告警事件和心跳，接收云端下发的干预指令、
 * OTA 升级包和媒体会话凭证。
 * </p>
 * <p>
 * 支持断线自动重连（指数退避），连接状态变更回调。
 * </p>
 * <p>
 * 设计依据：docs/ood_infrastructure.md §3.7.5、docs/ood_interface.md §2
 * </p>
 */
public class EdgeMqttClient {

    private static final Logger log = LoggerFactory.getLogger(EdgeMqttClient.class);

    private final EdgeProperties properties;
    private final String clientId;
    private IMqttAsyncClient client;

    private volatile boolean connected = false;
    private volatile boolean started = false;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_DELAY_SEC = 60;

    private final ScheduledExecutorService reconnectExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "edge-mqtt-reconnect");
                t.setDaemon(true);
                return t;
            });

    /** 连接状态变更回调 */
    private Consumer<Boolean> connectionStateCallback;

    /** 云端下行消息处理器 map（topic filter → handler） */
    private final Map<String, Consumer<String>> topicHandlers = new ConcurrentHashMap<>();

    public EdgeMqttClient(EdgeProperties properties) {
        this.properties = properties;
        this.clientId = properties.getClientIdPrefix() + "-" + properties.getDeviceId() + "-" +
                System.currentTimeMillis() % 100000;
    }

    /**
     * 启动 MQTT 客户端，连接 Broker。
     */
    public synchronized void start() {
        if (started) return;
        started = true;
        try {
            connect();
            subscribeTopics();
        } catch (MqttException e) {
            log.error("边缘 MQTT 启动失败: broker={}", properties.getBrokerUrl(), e);
            scheduleReconnect();
        }
    }

    /**
     * 停止 MQTT 客户端。
     */
    public synchronized void stop() {
        started = false;
        reconnectExecutor.shutdownNow();
        if (client != null) {
            try {
                client.disconnect();
                client.close();
            } catch (MqttException e) {
                log.warn("边缘 MQTT 关闭异常", e);
            }
        }
        connected = false;
        notifyStateChange();
    }

    /**
     * 向云端发布消息。
     *
     * @param topic   MQTT 主题
     * @param payload JSON 字符串负载
     * @param qos     QoS 等级
     * @return true 如果发送成功（至少被 Broker 确认）
     */
    public boolean publish(String topic, String payload, int qos) {
        if (!connected || client == null) {
            log.warn("边缘 MQTT 未连接，消息进入离线缓冲: topic={}", topic);
            return false;
        }
        try {
            MqttMessage msg = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            msg.setQos(qos);
            msg.setRetained(false);
            client.publish(topic, msg).waitForCompletion(5000);
            return true;
        } catch (MqttException e) {
            log.error("边缘 MQTT 发布失败: topic={}", topic, e);
            return false;
        }
    }

    /**
     * 注册连接状态变更回调。
     */
    public void onConnectionStateChange(Consumer<Boolean> callback) {
        this.connectionStateCallback = callback;
    }

    /**
     * 注册云端下发消息处理器。
     *
     * @param topicFilter MQTT 主题过滤器
     * @param handler     消息处理器（接收 payload 字符串）
     */
    public void registerHandler(String topicFilter, Consumer<String> handler) {
        topicHandlers.put(topicFilter, handler);
        if (connected) {
            subscribe(topicFilter);
        }
    }

    public boolean isConnected() {
        return connected;
    }

    // ── 内部实现 ──

    private void connect() throws MqttException {
        client = new MqttAsyncClient(properties.getBrokerUrl(), clientId);
        client.setCallback(createCallback());

        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setAutomaticReconnect(false);
        options.setCleanStart(true);
        options.setConnectionTimeout(30);
        options.setKeepAliveInterval(30);
        if (!properties.getMqttUsername().isEmpty()) {
            options.setUserName(properties.getMqttUsername());
            options.setPassword(properties.getMqttPassword().getBytes(StandardCharsets.UTF_8));
        }

        log.info("边缘 MQTT 连接中: broker={}, clientId={}", properties.getBrokerUrl(), clientId);
        IMqttToken token = client.connect(options);
        token.waitForCompletion(30000L);
        connected = true;
        reconnectAttempts = 0;
        notifyStateChange();
        log.info("边缘 MQTT 已连接: broker={}", properties.getBrokerUrl());
    }

    private MqttCallback createCallback() {
        return new MqttCallback() {
            @Override
            public void disconnected(MqttDisconnectResponse disconnectResponse) {
                connected = false;
                notifyStateChange();
                log.warn("边缘 MQTT 断开连接: reason={}", disconnectResponse.getReasonString());
                scheduleReconnect();
            }

            @Override
            public void mqttErrorOccurred(MqttException exception) {
                log.error("边缘 MQTT 错误", exception);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                log.debug("边缘 MQTT 收到下行: topic={}", topic);
                for (Map.Entry<String, Consumer<String>> entry : topicHandlers.entrySet()) {
                    if (MqttTopicValidator.isMatched(entry.getKey(), topic)) {
                        try {
                            entry.getValue().accept(payload);
                        } catch (Exception e) {
                            log.error("边缘 MQTT 下行处理异常: topic={}", topic, e);
                        }
                    }
                }
            }

            @Override
            public void deliveryComplete(IMqttToken token) {}
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                connected = true;
                reconnectAttempts = 0;
                notifyStateChange();
                log.info("边缘 MQTT 重新连接完成: server={}", serverURI);
                subscribeTopics();
            }
            @Override
            public void authPacketArrived(int reasonCode,
                                           org.eclipse.paho.mqttv5.common.packet.MqttProperties properties) {}
        };
    }

    private void subscribeTopics() {
        for (String topicFilter : topicHandlers.keySet()) {
            subscribe(topicFilter);
        }
    }

    private void subscribe(String topicFilter) {
        if (client == null || !connected) return;
        try {
            client.subscribe(topicFilter, 1);
            log.info("边缘 MQTT 订阅: filter={}", topicFilter);
        } catch (MqttException e) {
            log.error("边缘 MQTT 订阅失败: filter={}", topicFilter, e);
        }
    }

    private void scheduleReconnect() {
        if (!started) return;
        reconnectExecutor.schedule(this::tryReconnect,
                reconnectDelaySeconds(), TimeUnit.SECONDS);
    }

    private long reconnectDelaySeconds() {
        reconnectAttempts++;
        return Math.min((long) Math.pow(2, reconnectAttempts), MAX_RECONNECT_DELAY_SEC);
    }

    private void tryReconnect() {
        if (!started || connected) return;
        try {
            if (client != null && !client.isConnected()) {
                client.reconnect();
            } else {
                connect();
                subscribeTopics();
            }
        } catch (MqttException e) {
            log.warn("边缘 MQTT 重连失败: {}", e.getMessage());
            scheduleReconnect();
        }
    }

    private void notifyStateChange() {
        if (connectionStateCallback != null) {
            try {
                connectionStateCallback.accept(connected);
            } catch (Exception e) {
                log.error("连接状态回调异常", e);
            }
        }
    }
}
