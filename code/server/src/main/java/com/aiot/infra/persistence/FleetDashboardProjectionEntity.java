package com.aiot.infra.persistence;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "t_fleet_dashboard_projection")
@IdClass(FleetDashboardProjectionEntity.ProjectionId.class)
public class FleetDashboardProjectionEntity {

    @Id
    @Column(name = "fleet_id", length = 64)
    private String fleetId;

    @Id
    @Column(name = "risk_level", length = 16)
    private String riskLevel;

    @Id
    @Column(name = "alert_type", length = 32)
    private String alertType;

    @Column(name = "alert_count")
    private Integer alertCount;

    @Column(name = "driver_count")
    private Integer driverCount;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }

    public String getFleetId() { return fleetId; }
    public void setFleetId(String fleetId) { this.fleetId = fleetId; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }
    public Integer getAlertCount() { return alertCount; }
    public void setAlertCount(Integer alertCount) { this.alertCount = alertCount; }
    public Integer getDriverCount() { return driverCount; }
    public void setDriverCount(Integer driverCount) { this.driverCount = driverCount; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public static class ProjectionId implements Serializable {
        private String fleetId;
        private String riskLevel;
        private String alertType;
        public ProjectionId() {}
        public String getFleetId() { return fleetId; }
        public void setFleetId(String fleetId) { this.fleetId = fleetId; }
        public String getRiskLevel() { return riskLevel; }
        public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
        public String getAlertType() { return alertType; }
        public void setAlertType(String alertType) { this.alertType = alertType; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof ProjectionId)) return false;
            ProjectionId that = (ProjectionId) o;
            return fleetId.equals(that.fleetId) && riskLevel.equals(that.riskLevel) && alertType.equals(that.alertType);
        }
        @Override public int hashCode() { return fleetId.hashCode() + riskLevel.hashCode() + alertType.hashCode(); }
    }
}
