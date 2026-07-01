package com.aiot.infra.eventbus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DeadLetterHandler 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class DeadLetterHandlerTest {

    @Mock
    private DeadLetterRepository deadLetterRepository;

    private DeadLetterHandler deadLetterHandler;

    @BeforeEach
    void setUp() {
        deadLetterHandler = new DeadLetterHandler(deadLetterRepository);
    }

    @Test
    void moveToDeadLetter_shouldCreateDeadLetterEntity() {
        OutboxEventEntity outboxEvent = createOutboxEvent();
        when(deadLetterRepository.save(any(DeadLetterEntity.class))).thenAnswer(i -> i.getArgument(0));

        deadLetterHandler.moveToDeadLetter(outboxEvent);

        ArgumentCaptor<DeadLetterEntity> captor = ArgumentCaptor.forClass(DeadLetterEntity.class);
        verify(deadLetterRepository, times(1)).save(captor.capture());

        DeadLetterEntity savedEntity = captor.getValue();
        assertEquals(outboxEvent.getEventId(), savedEntity.getEventId());
        assertEquals(outboxEvent.getEventType(), savedEntity.getEventType());
        assertEquals(outboxEvent.getAggregateId(), savedEntity.getAggregateId());
        assertEquals(outboxEvent.getPayload(), savedEntity.getPayload());
        assertEquals(outboxEvent.getOccurredAt(), savedEntity.getOccurredAt());
        assertEquals(outboxEvent.getRetryCount(), savedEntity.getRetryCount());
        assertNotNull(savedEntity.getDlqId());
        assertNotNull(savedEntity.getMovedAt());
    }

    @Test
    void moveToDeadLetter_shouldUseLastErrorFromOutboxEvent() {
        OutboxEventEntity outboxEvent = createOutboxEvent();
        outboxEvent.setLastError("Connection timeout");
        when(deadLetterRepository.save(any(DeadLetterEntity.class))).thenAnswer(i -> i.getArgument(0));

        deadLetterHandler.moveToDeadLetter(outboxEvent);

        ArgumentCaptor<DeadLetterEntity> captor = ArgumentCaptor.forClass(DeadLetterEntity.class);
        verify(deadLetterRepository).save(captor.capture());
        assertEquals("Connection timeout", captor.getValue().getLastError());
    }

    @Test
    void moveToDeadLetter_shouldUseDefaultErrorWhenLastErrorIsNull() {
        OutboxEventEntity outboxEvent = createOutboxEvent();
        outboxEvent.setLastError(null);
        when(deadLetterRepository.save(any(DeadLetterEntity.class))).thenAnswer(i -> i.getArgument(0));

        deadLetterHandler.moveToDeadLetter(outboxEvent);

        ArgumentCaptor<DeadLetterEntity> captor = ArgumentCaptor.forClass(DeadLetterEntity.class);
        verify(deadLetterRepository).save(captor.capture());
        assertEquals("Unknown error", captor.getValue().getLastError());
    }

    @Test
    void cleanupExpiredDeadLetters_shouldDoNothingWhenNoExpiredEvents() {
        when(deadLetterRepository.findByMovedAtBefore(any(Instant.class))).thenReturn(Collections.emptyList());
        deadLetterHandler.cleanupExpiredDeadLetters();
        verify(deadLetterRepository, never()).deleteByMovedAtBefore(any(Instant.class));
    }

    @Test
    void cleanupExpiredDeadLetters_shouldDeleteExpiredEvents() {
        DeadLetterEntity expiredEvent1 = createDeadLetterEntity();
        DeadLetterEntity expiredEvent2 = createDeadLetterEntity();
        when(deadLetterRepository.findByMovedAtBefore(any(Instant.class))).thenReturn(List.of(expiredEvent1, expiredEvent2));
        when(deadLetterRepository.deleteByMovedAtBefore(any(Instant.class))).thenReturn(2);

        deadLetterHandler.cleanupExpiredDeadLetters();

        verify(deadLetterRepository, times(1)).deleteByMovedAtBefore(any(Instant.class));
    }

    @Test
    void getAllDeadLetters_shouldReturnAllEvents() {
        DeadLetterEntity event1 = createDeadLetterEntity();
        DeadLetterEntity event2 = createDeadLetterEntity();
        when(deadLetterRepository.findAllByOrderByMovedAtDesc()).thenReturn(List.of(event1, event2));

        List<DeadLetterEntity> result = deadLetterHandler.getAllDeadLetters();

        assertEquals(2, result.size());
        verify(deadLetterRepository, times(1)).findAllByOrderByMovedAtDesc();
    }

    @Test
    void countDeadLetters_shouldReturnCount() {
        when(deadLetterRepository.countBy()).thenReturn(5L);
        long count = deadLetterHandler.countDeadLetters();
        assertEquals(5L, count);
        verify(deadLetterRepository, times(1)).countBy();
    }

    private OutboxEventEntity createOutboxEvent() {
        OutboxEventEntity event = new OutboxEventEntity(
                "event-123", "TestEvent", "aggregate-123", "TRIP",
                "{\"eventId\":\"event-123\"}", Instant.now()
        );
        event.setRetryCount(10);
        event.setLastError("Max retries exceeded");
        return event;
    }

    private DeadLetterEntity createDeadLetterEntity() {
        return new DeadLetterEntity(
                "event-123", "TestEvent", "aggregate-123",
                "{\"eventId\":\"event-123\"}", Instant.now(), 10, "Max retries exceeded"
        );
    }
}
