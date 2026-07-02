package com.aiot.infra.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;

@Entity
@Table(name = "t_trip")
public class TripJpaEntity {

    @Id
    @Column(name = "trip_id", length = 36)
    private String tripId;

    @Version
    private Integer version;

    @Column(nullable = false, length = 36)
    private String driverId;

    @Column(nullable = false, length = 36)
    private String vehicleId;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    private Integer hardBrakingCount;

    private Integer hardAccelerationCount;

    private Integer scoreValue;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }

    public String getTripId() { return tripId; }
    public void setTripId(String tripId) { this.tripId = tripId; }
    public Integer getVersion() { return version; }
    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }
    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }
    public Integer getHardBrakingCount() { return hardBrakingCount; }
    public void setHardBrakingCount(Integer hardBrakingCount) { this.hardBrakingCount = hardBrakingCount; }
    public Integer getHardAccelerationCount() { return hardAccelerationCount; }
    public void setHardAccelerationCount(Integer hardAccelerationCount) { this.hardAccelerationCount = hardAccelerationCount; }
    public Integer getScoreValue() { return scoreValue; }
    public void setScoreValue(Integer scoreValue) { this.scoreValue = scoreValue; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
