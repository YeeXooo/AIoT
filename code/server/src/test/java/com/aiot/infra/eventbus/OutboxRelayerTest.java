package com.aiot.infra.eventbus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OutboxRelayer 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class OutboxRelayerTest {

    @Mock
    private OutboxEventRepository outboxRepository;

    @Mock
    private DeadLetterHandler deadLetterHandler;

    @Mock
    private OutboxRelayer.EventPublisher eventPublisher;

    private OutboxRelayer outboxRelayer;

    @BeforeEach
    void setUp() {
        outboxRelayer = new OutboxRelayer(outboxRepository, deadLetterHandler, eventPublisher);
    }

    @Test
    void pollAndDeliver_shouldDoNothingWhenNoPendingEvents() {
        when(outboxRepository.findPendingEvents(any(Instant.class), anyInt())).thenReturn(Collections.emptyList());
        outboxRelayer.pollAndDeliver();
        verify(outboxRepository, never()).save(any());
        verify(eventPublisher, never()).publish(anyString(), anyString());
    }

    @Test
    void pollAndDeliver_shouldDeliverNewEvent() {
        OutboxEventEntity event = createOutboxEvent(0, null);
        when(outboxRepository.findPendingEvents(any(Instant.class), anyInt())).thenReturn(List.of(event));
        when(outboxRepository.save(any(OutboxEventEntity.class))).thenAnswer(i -> i.getArgument(0));

        outboxRelayer.pollAndDeliver();

        verify(eventPublisher, times(1)).publish("TestEvent", event.getPayload());
        verify(outboxRepository, times(1)).save(argThat(e -> e.isPublished()));
    }

    @Test
    void pollAndDeliver_shouldNotDeliverEventInBackoffPeriod() {
        OutboxEventEntity event = createOutboxEvent(1, Instant.now().minusSeconds(1));
        when(outboxRepository.findPendingEvents(any(Instant.class), anyInt())).thenReturn(List.of(event));

        outboxRelayer.pollAndDeliver();

        verify(eventPublisher, never()).publish(anyString(), anyString());
    }

    @Test
    void pollAndDeliver_shouldDeliverEventAfterBackoffPeriod() {
        OutboxEventEntity event = createOutboxEvent(1, Instant.now().minusSeconds(3));
        when(outboxRepository.findPendingEvents(any(Instant.class), anyInt())).thenReturn(List.of(event));
        when(outboxRepository.save(any(OutboxEventEntity.class))).thenAnswer(i -> i.getArgument(0));

        outboxRelayer.pollAndDeliver();

        verify(eventPublisher, times(1)).publish("TestEvent", event.getPayload());
    }

    @Test
    void pollAndDeliver_shouldMoveToDLQWhenMaxRetriesExceeded() {
        OutboxEventEntity event = createOutboxEvent(10, Instant.now().minusSeconds(61));
        when(outboxRepository.findPendingEvents(any(Instant.class), anyInt())).thenReturn(List.of(event));

        outboxRelayer.pollAndDeliver();

        verify(deadLetterHandler, times(1)).moveToDeadLetter(event);
        verify(outboxRepository, times(1)).delete(event);
        verify(eventPublisher, never()).publish(anyString(), anyString());
    }

    @Test
    void pollAndDeliver_shouldHandleDeliveryFailure() {
        OutboxEventEntity event = createOutboxEvent(0, null);
        when(outboxRepository.findPendingEvents(any(Instant.class), anyInt())).thenReturn(List.of(event));
        when(outboxRepository.save(any(OutboxEventEntity.class))).thenAnswer(i -> i.getArgument(0));
        doThrow(new RuntimeException("Delivery failed")).when(eventPublisher).publish(anyString(), anyString());

        outboxRelayer.pollAndDeliver();

        verify(outboxRepository, times(1)).save(argThat(e -> e.getRetryCount() == 1 && e.getLastError() != null));
    }

    @Test
    void pollAndDeliver_shouldProcessMultipleEvents() {
        OutboxEventEntity event1 = createOutboxEvent(0, null);
        OutboxEventEntity event2 = createOutboxEvent(0, null);
        when(outboxRepository.findPendingEvents(any(Instant.class), anyInt())).thenReturn(List.of(event1, event2));
        when(outboxRepository.save(any(OutboxEventEntity.class))).thenAnswer(i -> i.getArgument(0));

        outboxRelayer.pollAndDeliver();

        verify(eventPublisher, times(2)).publish(anyString(), anyString());
        verify(outboxRepository, times(2)).save(any(OutboxEventEntity.class));
    }

    @Test
    void pollAndDeliver_shouldNotMoveToDLQWhenRetryCountBelowMax() {
        OutboxEventEntity event = createOutboxEvent(9, Instant.now().minusSeconds(61));
        when(outboxRepository.findPendingEvents(any(Instant.class), anyInt())).thenReturn(List.of(event));
        when(outboxRepository.save(any(OutboxEventEntity.class))).thenAnswer(i -> i.getArgument(0));

        outboxRelayer.pollAndDeliver();

        verify(deadLetterHandler, never()).moveToDeadLetter(any());
        verify(eventPublisher, times(1)).publish(anyString(), anyString());
    }

    private OutboxEventEntity createOutboxEvent(int retryCount, Instant lastAttemptAt) {
        OutboxEventEntity event = new OutboxEventEntity(
                "event-123", "TestEvent", "aggregate-123", "TRIP",
                "{\"eventId\":\"event-123\"}", Instant.now()
        );
        event.setRetryCount(retryCount);
        event.setLastAttemptAt(lastAttemptAt);
        return event;
    }
}
