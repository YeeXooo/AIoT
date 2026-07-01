package com.aiot.domain.model;

import java.time.LocalDateTime;

public class Vehicle {
    private String vehicleId;
    private String licensePlate;
    private String vin;
    private String terminalSn;
    private String fleetId;
    private String firmwareVersion;
    private String sensorStatus;
    private LocalDateTime createdAt;

    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }
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
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
