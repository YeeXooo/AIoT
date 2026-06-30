package com.aiot.infra.persistence;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "t_alert_projection")
public class AlertProjectionEntity {

    @Id
    @Column(name = "alert_id", length = 36)
    private String alertId;

    @Column(nullable = false, length = 36)
    private String driverId;

    @Column(nullable = false, length = 36)
    private String vehicleId;

    @Column(length = 64)
    private String fleetId;

    @Column(nullable = false, length = 32)
    private String alertType;

    @Column(nullable = false, length = 16)
    private String riskLevel;

    private LocalDateTime resolvedAt;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @Column(length = 256)
    private String alertMsg;

    public String getAlertId() { return alertId; }
    public void setAlertId(String alertId) { this.alertId = alertId; }
    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }
    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }
    public String getFleetId() { return fleetId; }
    public void setFleetId(String fleetId) { this.fleetId = fleetId; }
    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    public String getAlertMsg() { return alertMsg; }
    public void setAlertMsg(String alertMsg) { this.alertMsg = alertMsg; }
}
