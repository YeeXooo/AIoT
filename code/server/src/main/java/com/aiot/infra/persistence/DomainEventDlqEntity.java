package com.aiot.infra.persistence;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "t_domain_event_dlq")
public class DomainEventDlqEntity {

    @Id
    @Column(name = "dlq_id", length = 36)
    private String dlqId;

    @Column(name = "original_event_id", nullable = false, length = 36)
    private String originalEventId;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime movedAt;

    public String getDlqId() { return dlqId; }
    public void setDlqId(String dlqId) { this.dlqId = dlqId; }
    public String getOriginalEventId() { return originalEventId; }
    public void setOriginalEventId(String originalEventId) { this.originalEventId = originalEventId; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getMovedAt() { return movedAt; }
    public void setMovedAt(LocalDateTime movedAt) { this.movedAt = movedAt; }
}
