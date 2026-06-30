package com.aiot.infra.persistence;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "t_domain_event_outbox")
public class DomainEventOutboxEntity {

    @Id
    @Column(name = "event_id", length = 36)
    private String eventId;

    @Column(name = "aggregate_id", nullable = false, length = 36)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "event_payload", nullable = false, columnDefinition = "TEXT")
    private String eventPayload;

    private Boolean published;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getEventPayload() { return eventPayload; }
    public void setEventPayload(String eventPayload) { this.eventPayload = eventPayload; }
    public Boolean getPublished() { return published; }
    public void setPublished(Boolean published) { this.published = published; }
    public LocalDateTime getLastAttemptAt() { return lastAttemptAt; }
    public void setLastAttemptAt(LocalDateTime lastAttemptAt) { this.lastAttemptAt = lastAttemptAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
