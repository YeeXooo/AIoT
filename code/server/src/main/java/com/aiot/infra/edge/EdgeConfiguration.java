package com.aiot.infra.edge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 边缘侧基础设施 Spring 条件配置。
 * <p>
 * 仅在 {@code aiot.edge.mode=edge} 时装配 SQLite 持久化、
 * MQTT 客户端和边缘-云端同步服务。云端模式（默认）不加载。
 * </p>
 * <p>
 * 设计依据：docs/ood_infrastructure.md §3.8
 * </p>
 */
@Configuration
@ConditionalOnProperty(name = "aiot.edge.mode", havingValue = "edge", matchIfMissing = false)
public class EdgeConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EdgeConfiguration.class);

    @Bean
    public EdgePersistenceService edgePersistenceService(EdgeProperties properties,
                                                          ObjectMapper objectMapper) {
        log.info("装配边缘侧 SQLite 持久化服务: path={}", properties.getSqlitePath());
        return new EdgePersistenceService(properties, objectMapper);
    }

    @Bean
    public EdgeMqttClient edgeMqttClient(EdgeProperties properties) {
        log.info("装配边缘侧 MQTT 客户端: broker={}, deviceId={}",
                properties.getBrokerUrl(), properties.getDeviceId());
        return new EdgeMqttClient(properties);
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public EdgeCloudSyncService edgeCloudSyncService(EdgePersistenceService persistence,
                                                       EdgeMqttClient mqttClient,
                                                       EdgeProperties properties,
                                                       ObjectMapper objectMapper) {
        log.info("装配边缘-云端同步服务: batchSize={}, retryInterval={}s",
                properties.getBatchSize(), properties.getRetryIntervalSec());
        return new EdgeCloudSyncService(persistence, mqttClient, properties, objectMapper);
    }
}
