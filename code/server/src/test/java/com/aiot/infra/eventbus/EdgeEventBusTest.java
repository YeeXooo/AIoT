package com.aiot.infra.eventbus;

import com.aiot.domain.event.DomainEvent;
import com.aiot.domain.shared.AggregateId;
import com.aiot.domain.shared.AggregateType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * EdgeEventBus 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class EdgeEventBusTest {

    @Mock
    private ApplicationEventPublisher springPublisher;

    private EdgeEventBus edgeEventBus;

    @BeforeEach
    void setUp() {
        edgeEventBus = new EdgeEventBus(springPublisher);
    }

    @Test
    void publish_shouldCallSpringPublisher() {
        DomainEvent event = new TestEvent();
        edgeEventBus.publish(event);
        verify(springPublisher, times(1)).publishEvent(event);
    }

    @Test
    void publish_shouldCallRegisteredSyncHandlers() {
        DomainEvent event = new TestEvent();
        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        edgeEventBus.registerSyncHandler("TestEvent", e -> handlerCalled.set(true));
        edgeEventBus.publish(event);
        assertTrue(handlerCalled.get());
    }

    @Test
    void publish_shouldCallMultipleHandlers() {
        DomainEvent event = new TestEvent();
        AtomicInteger callCount = new AtomicInteger(0);
        edgeEventBus.registerSyncHandler("TestEvent", e -> callCount.incrementAndGet());
        edgeEventBus.registerSyncHandler("TestEvent", e -> callCount.incrementAndGet());
        edgeEventBus.registerSyncHandler("TestEvent", e -> callCount.incrementAndGet());
        edgeEventBus.publish(event);
        assertEquals(3, callCount.get());
    }

    @Test
    void publish_shouldNotBlockOtherHandlersWhenOneThrows() {
        DomainEvent event = new TestEvent();
        AtomicBoolean secondHandlerCalled = new AtomicBoolean(false);
        edgeEventBus.registerSyncHandler("TestEvent", e -> { throw new RuntimeException("Handler error"); });
        edgeEventBus.registerSyncHandler("TestEvent", e -> secondHandlerCalled.set(true));
        edgeEventBus.publish(event);
        assertTrue(secondHandlerCalled.get());
        verify(springPublisher, times(1)).publishEvent(event);
    }

    @Test
    void registerSyncHandler_shouldAllowMultipleHandlersForSameEvent() {
        AtomicInteger callCount = new AtomicInteger(0);
        edgeEventBus.registerSyncHandler("TestEvent", e -> callCount.incrementAndGet());
        edgeEventBus.registerSyncHandler("TestEvent", e -> callCount.incrementAndGet());
        edgeEventBus.publish(new TestEvent());
        assertEquals(2, callCount.get());
    }

    @Test
    void registerAsyncHandler_shouldWorkAsSyncInEdgeMode() {
        DomainEvent event = new TestEvent();
        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        var subscription = edgeEventBus.registerAsyncHandler("TestEvent", e -> handlerCalled.set(true));
        edgeEventBus.publish(event);
        assertTrue(handlerCalled.get());
        assertTrue(subscription.isActive());
    }

    @Test
    void subscription_cancel_shouldRemoveHandler() {
        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        var subscription = edgeEventBus.registerAsyncHandler("TestEvent", e -> handlerCalled.set(true));
        assertTrue(subscription.isActive());
        subscription.cancel();
        assertFalse(subscription.isActive());
        edgeEventBus.publish(new TestEvent());
        assertFalse(handlerCalled.get());
    }

    @Test
    void unregisterSyncHandler_shouldRemoveAllHandlersForEventType() {
        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        edgeEventBus.registerSyncHandler("TestEvent", e -> handlerCalled.set(true));
        boolean result = edgeEventBus.unregisterSyncHandler("TestEvent");
        assertTrue(result);
        edgeEventBus.publish(new TestEvent());
        assertFalse(handlerCalled.get());
    }

    @Test
    void unregisterSyncHandler_shouldReturnFalseWhenNoHandlers() {
        boolean result = edgeEventBus.unregisterSyncHandler("NonExistentEvent");
        assertFalse(result);
    }

    @Test
    void publish_shouldNotCallHandlersForDifferentEventType() {
        DomainEvent event = new TestEvent();
        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        edgeEventBus.registerSyncHandler("OtherEvent", e -> handlerCalled.set(true));
        edgeEventBus.publish(event);
        assertFalse(handlerCalled.get());
    }

    private static class TestEvent implements DomainEvent {
        private final String eventId = UUID.randomUUID().toString();
        private final Instant occurredAt = Instant.now();
        private final AggregateId aggregateId = new AggregateId(UUID.randomUUID().toString(), AggregateType.TRIP);

        @Override public String eventId() { return eventId; }
        @Override public Instant occurredAt() { return occurredAt; }
        @Override public AggregateId aggregateId() { return aggregateId; }
    }
}
