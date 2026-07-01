package com.aiot.infra.eventbus;

import com.aiot.domain.event.DomainEvent;
import com.aiot.domain.event.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 云端侧事件总线实现。
 * <p>
 * 基于 Outbox 模式实现事件发布：publish() 将事件写入 domain_event_outbox 表，
 * 由 OutboxRelayer 异步投递至消息队列。
 * </p>
 * <p>
 * 设计依据：docs/ood_infrastructure.md §3.3.1 云端侧实现
 * </p>
 */
@Component
@ConditionalOnProperty(name = "aiot.eventbus.mode", havingValue = "cloud")
public class CloudEventBus implements DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(CloudEventBus.class);

    private final OutboxPersister outboxPersister;

    /**
     * 编程式注册的 handler 注册表（云端侧主要用于测试/回放场景）。
     */
    private final Map<String, Consumer<? extends DomainEvent>> asyncHandlers = new ConcurrentHashMap<>();

    public CloudEventBus(OutboxPersister outboxPersister) {
        this.outboxPersister = outboxPersister;
    }

    @Override
    public <E extends DomainEvent> void publish(E event) {
        String eventTypeName = event.getClass().getName();
        log.debug("Publishing event to outbox: {} (eventId={})", eventTypeName, event.eventId());

        // 写入 outbox 表（需在事务内调用）
        outboxPersister.persist(event);

        log.debug("Event persisted to outbox: {} (eventId={})", eventTypeName, event.eventId());
    }

    @Override
    public <E extends DomainEvent> void registerSyncHandler(String eventTypeName, Consumer<E> handler) {
        // 云端侧不支持同步 handler，仅记录日志
        log.warn("Sync handler registration not supported in cloud mode for event: {}", eventTypeName);
    }

    @Override
    public <E extends DomainEvent> Subscription registerAsyncHandler(String eventTypeName, Consumer<E> handler) {
        asyncHandlers.put(eventTypeName, handler);
        log.info("Registered async handler for event: {}", eventTypeName);

        return new Subscription() {
            @Override
            public void cancel() {
                asyncHandlers.remove(eventTypeName);
                log.info("Unregistered async handler for event: {}", eventTypeName);
            }

            @Override
            public boolean isActive() {
                return asyncHandlers.containsKey(eventTypeName);
            }
        };
    }

    @Override
    public boolean unregisterSyncHandler(String eventTypeName) {
        // 云端侧无同步 handler
        return false;
    }

    /**
     * 处理从消息队列消费的事件（由 OutboxRelayer 投递后触发）。
     *
     * @param event 领域事件
     */
    @SuppressWarnings("unchecked")
    public <E extends DomainEvent> void handleConsumedEvent(E event) {
        String eventTypeName = event.getClass().getName();
        Consumer<? extends DomainEvent> handler = asyncHandlers.get(eventTypeName);

        if (handler != null) {
            try {
                ((Consumer<E>) handler).accept(event);
                log.debug("Handled consumed event: {} (eventId={})", eventTypeName, event.eventId());
            } catch (Exception e) {
                log.error("Handler error for consumed event {}: {}", eventTypeName, e.getMessage(), e);
            }
        } else {
            log.debug("No handler registered for consumed event: {}", eventTypeName);
        }
    }
}
