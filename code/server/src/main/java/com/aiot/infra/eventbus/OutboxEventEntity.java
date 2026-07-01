package com.aiot.infra.eventbus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Outbox 事件表 JPA 实体。
 * <p>
 * 设计依据：docs/ood_infrastructure.md §3.3.2
 * </p>
 */
@Entity
@Table(name = "domain_event_outbox")
public class OutboxEventEntity {

    @Id
    @Column(name = "event_id", length = 64)
    private String eventId;

    @Column(name = "event_type", length = 128, nullable = false)
    private String eventType;

    @Column(name = "aggregate_id", length = 256, nullable = false)
    private String aggregateId;

    @Column(name = "aggregate_type", length = 64, nullable = false)
    private String aggregateType;

    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "published", nullable = false)
    private boolean published;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    protected OutboxEventEntity() {
        // JPA 默认构造函数
    }

    public OutboxEventEntity(String eventId, String eventType, String aggregateId,
                             String aggregateType, String payload, Instant occurredAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.createdAt = Instant.now();
        this.published = false;
        this.retryCount = 0;
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

    public String getAggregateType() {
        return aggregateType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public void setLastAttemptAt(Instant lastAttemptAt) {
        this.lastAttemptAt = lastAttemptAt;
    }

    public boolean isPublished() {
        return published;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    /**
     * 标记为投递成功。
     */
    public void markPublished() {
        this.published = true;
        this.lastAttemptAt = Instant.now();
    }

    /**
     * 标记为投递失败，递增重试次数。
     */
    public void markFailed(String error) {
        this.retryCount++;
        this.lastAttemptAt = Instant.now();
        this.lastError = error;
    }
}
