package com.aiot.domain.model;

import java.time.LocalDateTime;

public class Trip {
    private String tripId;
    private String driverId;
    private String vehicleId;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Integer hardBrakingCount;
    private Integer hardAccelerationCount;
    private Integer scoreValue;
    private LocalDateTime createdAt;

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
    public Integer getHardBrakingCount() { return hardBrakingCount; }
    public void setHardBrakingCount(Integer hardBrakingCount) { this.hardBrakingCount = hardBrakingCount; }
    public Integer getHardAccelerationCount() { return hardAccelerationCount; }
    public void setHardAccelerationCount(Integer hardAccelerationCount) { this.hardAccelerationCount = hardAccelerationCount; }
    public Integer getScoreValue() { return scoreValue; }
    public void setScoreValue(Integer scoreValue) { this.scoreValue = scoreValue; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
