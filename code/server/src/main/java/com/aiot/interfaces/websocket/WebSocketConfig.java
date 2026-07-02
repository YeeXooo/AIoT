package com.aiot.interfaces.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置。
 * <p>
 * 注册家属 APP 和车队大屏的 WebSocket 端点处理器。
 * </p>
 * <p>
 * 设计依据：docs/ood_interface.md §3
 * </p>
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GuardianshipWebSocketHandler guardianshipHandler;
    private final FleetWebSocketHandler fleetHandler;
    private final WebSocketProperties properties;

    public WebSocketConfig(GuardianshipWebSocketHandler guardianshipHandler,
                           FleetWebSocketHandler fleetHandler,
                           WebSocketProperties properties) {
        this.guardianshipHandler = guardianshipHandler;
        this.fleetHandler = fleetHandler;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(guardianshipHandler, properties.getGuardianshipEndpoint())
                .setAllowedOriginPatterns("*");

        registry.addHandler(fleetHandler, properties.getFleetEndpoint())
                .setAllowedOriginPatterns("*");
    }
}
