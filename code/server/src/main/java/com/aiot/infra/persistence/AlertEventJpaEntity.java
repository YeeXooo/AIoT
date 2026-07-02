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
@Table(name = "t_safety_alert_event")
public class AlertEventJpaEntity {

    @Id
    @Column(name = "alert_id", length = 36)
    private String alertId;

    @Version
    private Integer version;

    @Column(nullable = false, length = 36)
    private String tripId;

    @Column(nullable = false, length = 36)
    private String driverId;

    @Column(nullable = false, length = 36)
    private String vehicleId;

    @Column(nullable = false, length = 32)
    private String alertType;

    @Column(nullable = false, length = 16)
    private String riskLevel;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @Column(length = 256)
    private String alertMsg;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }

    public String getAlertId() { return alertId; }
    public void setAlertId(String alertId) { this.alertId = alertId; }
    public Integer getVersion() { return version; }
    public String getTripId() { return tripId; }
    public void setTripId(String tripId) { this.tripId = tripId; }
    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }
    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }
    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    public String getAlertMsg() { return alertMsg; }
    public void setAlertMsg(String alertMsg) { this.alertMsg = alertMsg; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
