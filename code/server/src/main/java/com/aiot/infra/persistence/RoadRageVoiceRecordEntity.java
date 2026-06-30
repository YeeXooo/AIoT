package com.aiot.infra.persistence;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "t_road_rage_voice_record")
public class RoadRageVoiceRecordEntity {

    @Id
    @Column(name = "record_id", length = 36)
    private String recordId;

    @Version
    private Integer version;

    @Column(name = "alert_id", nullable = false, length = 36, unique = true)
    private String alertId;

    @Column(name = "trip_id", nullable = false, length = 36)
    private String tripId;

    @Column(name = "driver_id", nullable = false, length = 36)
    private String driverId;

    @Column(name = "vehicle_id", nullable = false, length = 36)
    private String vehicleId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "encrypted_file_path", columnDefinition = "TEXT")
    private String encryptedFilePath;

    @Column(name = "expiry_time", nullable = false)
    private LocalDateTime expiryTime;

    @Column(name = "is_sealed", nullable = false)
    private Boolean isSealed;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }

    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }
    public Integer getVersion() { return version; }
    public String getAlertId() { return alertId; }
    public void setAlertId(String alertId) { this.alertId = alertId; }
    public String getTripId() { return tripId; }
    public void setTripId(String tripId) { this.tripId = tripId; }
    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }
    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }
    public String getEncryptedFilePath() { return encryptedFilePath; }
    public void setEncryptedFilePath(String encryptedFilePath) { this.encryptedFilePath = encryptedFilePath; }
    public LocalDateTime getExpiryTime() { return expiryTime; }
    public void setExpiryTime(LocalDateTime expiryTime) { this.expiryTime = expiryTime; }
    public Boolean getIsSealed() { return isSealed; }
    public void setIsSealed(Boolean isSealed) { this.isSealed = isSealed; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
