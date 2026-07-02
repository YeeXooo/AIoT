package com.aiot.interfaces.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.mqttv5.client.IMqttAsyncClient;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.eclipse.paho.mqttv5.common.util.MqttTopicValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

/**
 * MQTT 客户端管理器。
 * <p>
 * 管理 Eclipse Paho MQTT5 客户端生命周期，包括连接、断线重连、
 * 主题订阅和消息分发。云端服务通过此类与 IoTDA Broker 通信，
 * 消费设备上行消息并下发指令。
 * </p>
 * <p>
 * 设计依据：docs/ood_interface.md §2、docs/ood_infrastructure.md §3.5.1
 * </p>
 */
public class MqttClientManager {

    private static final Logger log = LoggerFactory.getLogger(MqttClientManager.class);

    private final MqttProperties mqttProperties;
    private final ObjectMapper objectMapper;
    private final String clientId;
    private IMqttAsyncClient client;

    /** topic → handler 注册表 */
    private final Map<String, BiConsumer<String, byte[]>> topicHandlers = new ConcurrentHashMap<>();

    /** 重连调度器 */
    private final ScheduledExecutorService reconnectExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "mqtt-reconnect");
                t.setDaemon(true);
                return t;
            });

    private volatile boolean connected = false;
    private volatile boolean started = false;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_DELAY_SEC = 60;

    public MqttClientManager(MqttProperties mqttProperties, ObjectMapper objectMapper) {
        this.mqttProperties = mqttProperties;
        this.objectMapper = objectMapper;
        this.clientId = mqttProperties.getClientIdPrefix() + "-" +
                System.currentTimeMillis() % 100000;
    }

    /**
     * 启动 MQTT 客户端，连接 Broker 并订阅已注册的所有主题。
     */
    public synchronized void start() {
        if (started) return;
        started = true;

        if (!mqttProperties.isEnabled()) return;

        try {
            connect();
            subscribeAll();
        } catch (MqttException e) {
            log.error("MQTT 启动失败，将在后台自动重连: broker={}", mqttProperties.getBrokerUrl(), e);
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
                log.warn("MQTT 关闭异常", e);
            }
        }
        connected = false;
    }

    /**
     * 注册上行消息处理器。
     *
     * @param topicFilter 主题过滤器（支持 MQTT 通配符 +/#）
     * @param handler     消息处理器，接收 (topic, payloadBytes)
     */
    public void registerHandler(String topicFilter, BiConsumer<String, byte[]> handler) {
        topicHandlers.put(topicFilter, handler);
        if (connected) {
            subscribe(topicFilter);
        }
    }

    /**
     * 发布消息到指定主题。
     */
    public void publish(String topic, byte[] payload, int qos) {
        if (!connected || client == null) {
            log.warn("MQTT 未连接，消息丢弃: topic={}", topic);
            return;
        }
        try {
            MqttMessage msg = new MqttMessage(payload);
            msg.setQos(qos);
            msg.setRetained(false);
            client.publish(topic, msg);
        } catch (MqttException e) {
            log.error("MQTT 发布失败: topic={}", topic, e);
        }
    }

    /**
     * 发布 JSON 消息。
     */
    public void publishJson(String topic, Object payload, int qos) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(payload);
            publish(topic, bytes, qos);
        } catch (Exception e) {
            log.error("MQTT JSON 序列化失败: topic={}", topic, e);
        }
    }

    public boolean isConnected() {
        return connected;
    }

    // ── 内部实现 ──

    private void connect() throws MqttException {
        client = new MqttAsyncClient(mqttProperties.getBrokerUrl(), clientId);
        client.setCallback(createCallback());

        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setAutomaticReconnect(false);
        options.setCleanStart(true);
        options.setConnectionTimeout(mqttProperties.getConnectionTimeoutSec());
        options.setKeepAliveInterval(mqttProperties.getKeepAliveIntervalSec());
        if (mqttProperties.getUsername() != null && !mqttProperties.getUsername().isEmpty()) {
            options.setUserName(mqttProperties.getUsername());
            options.setPassword(mqttProperties.getPassword().getBytes(StandardCharsets.UTF_8));
        }

        log.info("MQTT 连接中: broker={}, clientId={}", mqttProperties.getBrokerUrl(), clientId);
        IMqttToken token = client.connect(options);
        token.waitForCompletion(mqttProperties.getConnectionTimeoutSec() * 1000L);
        connected = true;
        reconnectAttempts = 0;
        log.info("MQTT 已连接: broker={}", mqttProperties.getBrokerUrl());
    }

    private MqttCallback createCallback() {
        return new MqttCallback() {
            @Override
            public void disconnected(MqttDisconnectResponse disconnectResponse) {
                connected = false;
                log.warn("MQTT 断开连接: reason={}", disconnectResponse.getReasonString());
                scheduleReconnect();
            }

            @Override
            public void mqttErrorOccurred(MqttException exception) {
                log.error("MQTT 错误", exception);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                dispatchMessage(topic, message.getPayload());
            }

            @Override
            public void deliveryComplete(IMqttToken token) {
                // no-op
            }

            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                connected = true;
                reconnectAttempts = 0;
                log.info("MQTT 连接完成: reconnect={}, server={}", reconnect, serverURI);
                resubscribeAll();
            }

            @Override
            public void authPacketArrived(int reasonCode,
                                           org.eclipse.paho.mqttv5.common.packet.MqttProperties properties) {
                // no-op
            }
        };
    }

    private void dispatchMessage(String topic, byte[] payload) {
        String payloadStr = new String(payload, StandardCharsets.UTF_8);
        log.debug("MQTT 收到消息: topic={}, payload={}", topic,
                payloadStr.length() > 200 ? payloadStr.substring(0, 200) + "..." : payloadStr);

        for (Map.Entry<String, BiConsumer<String, byte[]>> entry : topicHandlers.entrySet()) {
            if (topicMatches(topic, entry.getKey())) {
                try {
                    entry.getValue().accept(topic, payload);
                } catch (Exception e) {
                    log.error("MQTT 消息处理异常: topic={}", topic, e);
                }
            }
        }
    }

    private boolean topicMatches(String topic, String filter) {
        if (!filter.contains("+") && !filter.contains("#")) {
            return topic.equals(filter);
        }
        return MqttTopicValidator.isMatched(filter, topic);
    }

    private void subscribeAll() {
        for (String topicFilter : topicHandlers.keySet()) {
            subscribe(topicFilter);
        }
    }

    private void resubscribeAll() {
        Set<String> filters = topicHandlers.keySet();
        if (filters.isEmpty()) return;
        try {
            MqttSubscription[] subs = filters.stream()
                    .map(f -> new MqttSubscription(f, mqttProperties.getDefaultQos()))
                    .toArray(MqttSubscription[]::new);
            client.subscribe(subs);
            log.info("MQTT 重新订阅 {} 个主题", subs.length);
        } catch (MqttException e) {
            log.error("MQTT 重新订阅失败", e);
        }
    }

    private void subscribe(String topicFilter) {
        if (client == null || !connected) return;
        try {
            client.subscribe(topicFilter, mqttProperties.getDefaultQos());
            log.info("MQTT 订阅: filter={}, qos={}", topicFilter, mqttProperties.getDefaultQos());
        } catch (MqttException e) {
            log.error("MQTT 订阅失败: filter={}", topicFilter, e);
        }
    }

    private void scheduleReconnect() {
        if (!started) return;
        reconnectExecutor.schedule(this::tryReconnect,
                reconnectDelaySeconds(), TimeUnit.SECONDS);
    }

    private long reconnectDelaySeconds() {
        reconnectAttempts++;
        long delay = Math.min((long) Math.pow(2, reconnectAttempts), MAX_RECONNECT_DELAY_SEC);
        log.info("MQTT 重连计划: 第{}次，延迟{}s", reconnectAttempts, delay);
        return delay;
    }

    private void tryReconnect() {
        if (!started || connected) return;
        try {
            if (client != null && !client.isConnected()) {
                client.reconnect();
            } else {
                connect();
                subscribeAll();
            }
        } catch (MqttException e) {
            log.warn("MQTT 重连失败: {}", e.getMessage());
            scheduleReconnect();
        }
    }
}
