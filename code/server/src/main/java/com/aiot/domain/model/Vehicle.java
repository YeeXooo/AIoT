package com.aiot.domain.model;

import com.aiot.domain.shared.VehicleId;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class Vehicle {

    private final VehicleId vehicleId;
    private String licensePlate;
    private String vin;
    private String terminalSn;
    private String fleetId;
    private OTAVersion firmwareVersion;
    private final Map<String, SensorStatus> sensorStatusMap;
    private boolean monitoringOffline;
    private OTAUpgradeStatus otaUpgradeStatus;
    private boolean doorLocked;
    private Integer version;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Vehicle(VehicleId vehicleId, String licensePlate, String vin, String terminalSn) {
        this.vehicleId = vehicleId;
        this.licensePlate = licensePlate;
        this.vin = vin;
        this.terminalSn = terminalSn;
        this.fleetId = null;
        this.firmwareVersion = OTAVersion.of("1.0.0");
        this.sensorStatusMap = new HashMap<>();
        this.monitoringOffline = false;
        this.otaUpgradeStatus = null;
        this.doorLocked = true;
        this.version = 1;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public static Vehicle register(String licensePlate, String vin, String terminalSn) {
        Objects.requireNonNull(licensePlate, "licensePlate must not be null");
        Objects.requireNonNull(vin, "vin must not be null");
        Objects.requireNonNull(terminalSn, "terminalSn must not be null");
        if (licensePlate.isBlank()) {
            throw new IllegalArgumentException("licensePlate must not be blank");
        }
        if (vin.isBlank()) {
            throw new IllegalArgumentException("vin must not be blank");
        }
        if (terminalSn.isBlank()) {
            throw new IllegalArgumentException("terminalSn must not be blank");
        }

        return new Vehicle(VehicleId.generate(), licensePlate, vin, terminalSn);
    }

    public void updateLicensePlate(String licensePlate) {
        Objects.requireNonNull(licensePlate, "licensePlate must not be null");
        if (licensePlate.isBlank()) {
            throw new IllegalArgumentException("licensePlate must not be blank");
        }
        this.licensePlate = licensePlate;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateFleetId(String fleetId) {
        this.fleetId = fleetId;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateFirmwareVersion(OTAVersion firmwareVersion) {
        Objects.requireNonNull(firmwareVersion, "firmwareVersion must not be null");
        this.firmwareVersion = firmwareVersion;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateSensorStatus(String sensorId, SensorStatus status) {
        Objects.requireNonNull(sensorId, "sensorId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        this.sensorStatusMap.put(sensorId, status);
        this.updatedAt = LocalDateTime.now();
    }

    public void updateMonitoringOffline(boolean offline) {
        this.monitoringOffline = offline;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateOTAUpgradeStatus(OTAUpgradeStatus status) {
        this.otaUpgradeStatus = status;
        this.updatedAt = LocalDateTime.now();
    }

    public void unlockDoors() {
        this.doorLocked = false;
        this.updatedAt = LocalDateTime.now();
    }

    public void lockDoors() {
        this.doorLocked = true;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean hasFailedSensors() {
        return sensorStatusMap.values().stream().anyMatch(s -> s == SensorStatus.FAILED);
    }

    public void validate() {
        Objects.requireNonNull(vehicleId, "vehicleId must not be null");
        Objects.requireNonNull(licensePlate, "licensePlate must not be null");
        Objects.requireNonNull(vin, "vin must not be null");
        Objects.requireNonNull(terminalSn, "terminalSn must not be null");
        Objects.requireNonNull(firmwareVersion, "firmwareVersion must not be null");
        if (licensePlate.isBlank()) {
            throw new IllegalStateException("licensePlate must not be blank");
        }
        if (vin.isBlank()) {
            throw new IllegalStateException("vin must not be blank");
        }
        if (terminalSn.isBlank()) {
            throw new IllegalStateException("terminalSn must not be blank");
        }
    }

    public VehicleId vehicleId() { return vehicleId; }
    public String licensePlate() { return licensePlate; }
    public String vin() { return vin; }
    public String terminalSn() { return terminalSn; }
    public Optional<String> fleetId() { return Optional.ofNullable(fleetId); }
    public OTAVersion firmwareVersion() { return firmwareVersion; }
    public Map<String, SensorStatus> sensorStatusMap() { return Map.copyOf(sensorStatusMap); }
    public boolean isMonitoringOffline() { return monitoringOffline; }
    public Optional<OTAUpgradeStatus> otaUpgradeStatus() { return Optional.ofNullable(otaUpgradeStatus); }
    public boolean isDoorLocked() { return doorLocked; }
    public Integer version() { return version; }
    public void version(Integer version) { this.version = version; }
    public LocalDateTime createdAt() { return createdAt; }
    public LocalDateTime updatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vehicle vehicle = (Vehicle) o;
        return Objects.equals(vehicleId, vehicle.vehicleId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(vehicleId);
    }
}