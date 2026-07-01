package com.aiot.domain.model;

import java.time.LocalDateTime;

public class AlertEvent {
    private String alertId;
    private String tripId;
    private String driverId;
    private String vehicleId;
    private String alertType;
    private String riskLevel;
    private LocalDateTime occurredAt;
    private String alertMsg;

    public String getAlertId() { return alertId; }
    public void setAlertId(String alertId) { this.alertId = alertId; }
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
}
