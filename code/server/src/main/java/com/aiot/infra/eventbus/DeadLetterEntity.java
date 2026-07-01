package com.aiot.infra.eventbus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * 死信表 JPA 实体。
 * <p>
 * 设计依据：docs/ood_infrastructure.md §3.3.3
 * </p>
 */
@Entity
@Table(name = "domain_event_dlq")
public class DeadLetterEntity {

    @Id
    @Column(name = "dlq_id", length = 64)
    private String dlqId;

    @Column(name = "event_id", length = 64, nullable = false)
    private String eventId;

    @Column(name = "event_type", length = 128, nullable = false)
    private String eventType;

    @Column(name = "aggregate_id", length = 256, nullable = false)
    private String aggregateId;

    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "moved_at", nullable = false)
    private Instant movedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", columnDefinition = "TEXT", nullable = false)
    private String lastError;

    protected DeadLetterEntity() {
        // JPA 默认构造函数
    }

    public DeadLetterEntity(String eventId, String eventType, String aggregateId,
                            String payload, Instant occurredAt, int retryCount, String lastError) {
        this.dlqId = UUID.randomUUID().toString();
        this.eventId = eventId;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.movedAt = Instant.now();
        this.retryCount = retryCount;
        this.lastError = lastError;
    }

    public String getDlqId() {
        return dlqId;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getMovedAt() {
        return movedAt;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public String getLastError() {
        return lastError;
    }
}
