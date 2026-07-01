package com.aiot.infra.eventbus;

import com.aiot.domain.event.DomainEvent;
import com.aiot.domain.event.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 边缘侧事件总线实现。
 * <p>
 * 基于 Spring ApplicationEventPublisher + 编程式 handler 注册表实现同步事件发布。
 * 行为语义：publish(event) 在返回前同步执行所有已注册的消费方。
 * </p>
 * <p>
 * 设计依据：docs/ood_infrastructure.md §3.3.1 边缘侧实现
 * </p>
 */
@Component
@ConditionalOnProperty(name = "aiot.eventbus.mode", havingValue = "edge", matchIfMissing = true)
public class EdgeEventBus implements DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EdgeEventBus.class);

    private final ApplicationEventPublisher springPublisher;

    /**
     * 编程式注册的同步 handler 注册表。
     * Key: 事件类型名称（如 "RiskDeterminedEvent"）
     * Value: 该事件类型的 handler 列表
     */
    private final Map<String, List<Consumer<? extends DomainEvent>>> syncHandlers = new ConcurrentHashMap<>();

    public EdgeEventBus(ApplicationEventPublisher springPublisher) {
        this.springPublisher = springPublisher;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E extends DomainEvent> void publish(E event) {
        String eventTypeName = event.getClass().getSimpleName();
        log.debug("Publishing event: {} (eventId={})", eventTypeName, event.eventId());

        // 1. 同步执行编程式注册的 handler（边缘侧安全攸关链路）
        List<Consumer<? extends DomainEvent>> handlers = syncHandlers.get(eventTypeName);
        if (handlers != null && !handlers.isEmpty()) {
            for (Consumer<? extends DomainEvent> handler : handlers) {
                try {
                    ((Consumer<E>) handler).accept(event);
                } catch (Exception e) {
                    // 错误隔离：单个 handler 异常不阻断其他 handler
                    log.error("Handler error for event {}: {}", eventTypeName, e.getMessage(), e);
                }
            }
        }

        // 2. 发布至 Spring ApplicationEventPublisher（支持 @EventListener 声明式注册）
        try {
            springPublisher.publishEvent(event);
        } catch (Exception e) {
            // 错误隔离
            log.error("Spring event publish error for {}: {}", eventTypeName, e.getMessage(), e);
        }

        log.debug("Event published: {} (eventId={})", eventTypeName, event.eventId());
    }

    @Override
    public <E extends DomainEvent> void registerSyncHandler(String eventTypeName, Consumer<E> handler) {
        syncHandlers.computeIfAbsent(eventTypeName, k -> new CopyOnWriteArrayList<>()).add(handler);
        log.info("Registered sync handler for event: {}", eventTypeName);
    }

    @Override
    public <E extends DomainEvent> Subscription registerAsyncHandler(String eventTypeName, Consumer<E> handler) {
        // 边缘侧不区分同步/异步，统一作为同步 handler 处理
        List<Consumer<? extends DomainEvent>> handlers = syncHandlers.computeIfAbsent(
                eventTypeName, k -> new CopyOnWriteArrayList<>());
        handlers.add(handler);
        log.info("Registered async handler (as sync in edge mode) for event: {}", eventTypeName);

        return new Subscription() {
            @Override
            public void cancel() {
                handlers.remove(handler);
                log.info("Unregistered handler for event: {}", eventTypeName);
            }

            @Override
            public boolean isActive() {
                return handlers.contains(handler);
            }
        };
    }

    @Override
    public boolean unregisterSyncHandler(String eventTypeName) {
        List<Consumer<? extends DomainEvent>> removed = syncHandlers.remove(eventTypeName);
        if (removed != null) {
            log.info("Unregistered all sync handlers for event: {}", eventTypeName);
            return true;
        }
        return false;
    }
}
