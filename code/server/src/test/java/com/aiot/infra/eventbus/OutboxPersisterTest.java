package com.aiot.infra.eventbus;

import com.aiot.domain.event.DomainEvent;
import com.aiot.domain.shared.AggregateId;
import com.aiot.domain.shared.AggregateType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * OutboxPersister 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class OutboxPersisterTest {

    @Mock
    private OutboxEventRepository outboxRepository;

    private OutboxPersister outboxPersister;

    @BeforeEach
    void setUp() {
        outboxPersister = new OutboxPersister(outboxRepository);
    }

    @Test
    void persist_shouldSaveEventToRepository() {
        DomainEvent event = new TestEvent();
        when(outboxRepository.save(any(OutboxEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        outboxPersister.persist(event);

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxRepository, times(1)).save(captor.capture());

        OutboxEventEntity savedEntity = captor.getValue();
        assertEquals(event.eventId(), savedEntity.getEventId());
        assertEquals("TestEvent", savedEntity.getEventType());
        assertEquals(event.aggregateId().value(), savedEntity.getAggregateId());
        assertEquals("TRIP", savedEntity.getAggregateType());
        assertNotNull(savedEntity.getPayload());
        assertEquals(event.occurredAt(), savedEntity.getOccurredAt());
        assertFalse(savedEntity.isPublished());
        assertEquals(0, savedEntity.getRetryCount());
    }

    @Test
    void persist_shouldSerializeEventToJson() {
        DomainEvent event = new TestEvent();
        when(outboxRepository.save(any(OutboxEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        outboxPersister.persist(event);

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxRepository).save(captor.capture());

        String payload = captor.getValue().getPayload();
        assertNotNull(payload);
        assertTrue(payload.contains("eventId"));
        assertTrue(payload.contains("occurredAt"));
        assertTrue(payload.contains("aggregateId"));
    }

    @Test
    void persistAll_shouldSaveMultipleEvents() {
        DomainEvent event1 = new TestEvent();
        DomainEvent event2 = new TestEvent();
        when(outboxRepository.save(any(OutboxEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        outboxPersister.persistAll(java.util.List.of(event1, event2));

        verify(outboxRepository, times(2)).save(any(OutboxEventEntity.class));
    }

    @Test
    void persist_shouldThrowWhenRepositoryFails() {
        DomainEvent event = new TestEvent();
        when(outboxRepository.save(any(OutboxEventEntity.class))).thenThrow(new RuntimeException("Database error"));

        assertThrows(RuntimeException.class, () -> outboxPersister.persist(event));
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
