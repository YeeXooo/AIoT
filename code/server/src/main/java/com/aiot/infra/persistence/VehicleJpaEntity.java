package com.aiot.infra.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "t_vehicle")
public class VehicleJpaEntity {

    @Id
    @Column(name = "vehicle_id", length = 36)
    private String vehicleId;

    @Version
    private Integer version;

    @Column(nullable = false, length = 32)
    private String licensePlate;

    @Column(nullable = false, length = 64)
    private String vin;

    @Column(nullable = false, length = 64)
    private String terminalSn;

    @Column(length = 64)
    private String fleetId;

    @Column(length = 64)
    private String firmwareVersion;

    @Column(length = 32)
    private String sensorStatus;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }

    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }
    public Integer getVersion() { return version; }
    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }
    public String getVin() { return vin; }
    public void setVin(String vin) { this.vin = vin; }
    public String getTerminalSn() { return terminalSn; }
    public void setTerminalSn(String terminalSn) { this.terminalSn = terminalSn; }
    public String getFleetId() { return fleetId; }
    public void setFleetId(String fleetId) { this.fleetId = fleetId; }
    public String getFirmwareVersion() { return firmwareVersion; }
    public void setFirmwareVersion(String firmwareVersion) { this.firmwareVersion = firmwareVersion; }
    public String getSensorStatus() { return sensorStatus; }
    public void setSensorStatus(String sensorStatus) { this.sensorStatus = sensorStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
