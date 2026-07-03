package com.aiot.interfaces.mqtt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MQTT 模块 Spring 配置。
 * <p>
 * 装配 MQTT 设备鉴权提供者和设备网关配置。
 * MqttClientManager 由 MqttDeviceGateway 内部管理，无需单独 Bean 暴露。
 * </p>
 */
@Configuration
public class MqttConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MqttConfiguration.class);

    @Bean
    public MqttDeviceAuthProvider mqttDeviceAuthProvider(MqttProperties properties) {
        log.info("初始化 MQTT 设备鉴权提供者: iotda={}, deviceTokenTtl={}s",
                properties.isIotda(), properties.getDeviceTokenTtlSec());
        return new MqttDeviceAuthProvider(properties);
    }
}
