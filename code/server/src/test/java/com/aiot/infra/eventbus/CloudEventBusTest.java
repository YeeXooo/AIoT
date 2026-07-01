package com.aiot.infra.eventbus;

import com.aiot.domain.event.DomainEvent;
import com.aiot.domain.shared.AggregateId;
import com.aiot.domain.shared.AggregateType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * CloudEventBus 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class CloudEventBusTest {

    @Mock
    private OutboxPersister outboxPersister;

    private CloudEventBus cloudEventBus;

    @BeforeEach
    void setUp() {
        cloudEventBus = new CloudEventBus(outboxPersister);
    }

    @Test
    void publish_shouldPersistEventToOutbox() {
        DomainEvent event = new TestEvent();
        cloudEventBus.publish(event);
        verify(outboxPersister, times(1)).persist(event);
    }

    @Test
    void registerSyncHandler_shouldLogWarning() {
        DomainEvent event = new TestEvent();
        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        cloudEventBus.registerSyncHandler("TestEvent", e -> handlerCalled.set(true));
        cloudEventBus.publish(event);
        assertFalse(handlerCalled.get());
    }

    @Test
    void registerAsyncHandler_shouldReturnActiveSubscription() {
        var subscription = cloudEventBus.registerAsyncHandler("TestEvent", e -> {});
        assertNotNull(subscription);
        assertTrue(subscription.isActive());
    }

    @Test
    void subscription_cancel_shouldDeactivateSubscription() {
        var subscription = cloudEventBus.registerAsyncHandler("TestEvent", e -> {});
        subscription.cancel();
        assertFalse(subscription.isActive());
    }

    @Test
    void handleConsumedEvent_shouldCallRegisteredHandler() {
        DomainEvent event = new TestEvent();
        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        cloudEventBus.registerAsyncHandler(TestEvent.class.getName(), e -> handlerCalled.set(true));
        cloudEventBus.handleConsumedEvent(event);
        assertTrue(handlerCalled.get());
    }

    @Test
    void handleConsumedEvent_shouldNotCallHandlerForDifferentEventType() {
        DomainEvent event = new TestEvent();
        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        cloudEventBus.registerAsyncHandler("OtherEvent", e -> handlerCalled.set(true));
        cloudEventBus.handleConsumedEvent(event);
        assertFalse(handlerCalled.get());
    }

    @Test
    void handleConsumedEvent_shouldHandleExceptionInHandler() {
        DomainEvent event = new TestEvent();
        cloudEventBus.registerAsyncHandler("TestEvent", e -> { throw new RuntimeException("Handler error"); });
        assertDoesNotThrow(() -> cloudEventBus.handleConsumedEvent(event));
    }

    @Test
    void unregisterSyncHandler_shouldReturnFalse() {
        boolean result = cloudEventBus.unregisterSyncHandler("TestEvent");
        assertFalse(result);
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
