package com.aiot.domain.model;

import com.aiot.domain.shared.AlertId;
import com.aiot.domain.shared.RoadRageVoiceRecordId;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

public class RoadRageVoiceRecord {

    private final RoadRageVoiceRecordId recordId;
    private final AlertId alertId;
    private final LocalDateTime recordedAt;
    private String encryptedAudioReference;
    private boolean anonymized;
    private LocalDateTime retentionExpiresAt;
    private boolean sealed;
    private Integer version;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private RoadRageVoiceRecord(RoadRageVoiceRecordId recordId, AlertId alertId, LocalDateTime recordedAt) {
        this.recordId = recordId;
        this.alertId = alertId;
        this.recordedAt = recordedAt;
        this.encryptedAudioReference = null;
        this.anonymized = false;
        this.retentionExpiresAt = recordedAt.plusDays(30);
        this.sealed = false;
        this.version = 1;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public static RoadRageVoiceRecord create(AlertId alertId, LocalDateTime recordedAt) {
        Objects.requireNonNull(alertId, "alertId must not be null");
        Objects.requireNonNull(recordedAt, "recordedAt must not be null");

        return new RoadRageVoiceRecord(RoadRageVoiceRecordId.generate(), alertId, recordedAt);
    }

    public void setEncryptedAudioReference(String encryptedAudioReference) {
        Objects.requireNonNull(encryptedAudioReference, "encryptedAudioReference must not be null");
        if (encryptedAudioReference.isBlank()) {
            throw new IllegalArgumentException("encryptedAudioReference must not be blank");
        }
        if (sealed) {
            throw new IllegalStateException("Cannot modify sealed record");
        }
        this.encryptedAudioReference = encryptedAudioReference;
        this.updatedAt = LocalDateTime.now();
    }

    public void markAnonymized() {
        if (sealed) {
            throw new IllegalStateException("Cannot modify sealed record");
        }
        this.anonymized = true;
        this.updatedAt = LocalDateTime.now();
    }

    public void seal() {
        this.sealed = true;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateRetentionExpiresAt(LocalDateTime retentionExpiresAt) {
        Objects.requireNonNull(retentionExpiresAt, "retentionExpiresAt must not be null");
        if (sealed) {
            throw new IllegalStateException("Cannot modify sealed record");
        }
        this.retentionExpiresAt = retentionExpiresAt;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isExpired(LocalDateTime referenceTime) {
        Objects.requireNonNull(referenceTime, "referenceTime must not be null");
        return retentionExpiresAt != null && referenceTime.isAfter(retentionExpiresAt);
    }

    public boolean isAccessibleForAudit() {
        return sealed && anonymized;
    }

    public void validate() {
        Objects.requireNonNull(recordId, "recordId must not be null");
        Objects.requireNonNull(alertId, "alertId must not be null");
        Objects.requireNonNull(recordedAt, "recordedAt must not be null");
        Objects.requireNonNull(retentionExpiresAt, "retentionExpiresAt must not be null");
        if (encryptedAudioReference != null && encryptedAudioReference.isBlank()) {
            throw new IllegalStateException("encryptedAudioReference must not be blank if set");
        }
    }

    public RoadRageVoiceRecordId recordId() { return recordId; }
    public AlertId alertId() { return alertId; }
    public LocalDateTime recordedAt() { return recordedAt; }
    public Optional<String> encryptedAudioReference() { return Optional.ofNullable(encryptedAudioReference); }
    public boolean isAnonymized() { return anonymized; }
    public LocalDateTime retentionExpiresAt() { return retentionExpiresAt; }
    public boolean isSealed() { return sealed; }
    public Integer version() { return version; }
    public void version(Integer version) { this.version = version; }
    public LocalDateTime createdAt() { return createdAt; }
    public LocalDateTime updatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoadRageVoiceRecord that = (RoadRageVoiceRecord) o;
        return Objects.equals(recordId, that.recordId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(recordId);
    }
}