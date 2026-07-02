package com.aiot.domain.model;

import com.aiot.domain.event.AlertType;
import com.aiot.domain.event.RiskLevel;
import com.aiot.domain.shared.AlertId;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.TripId;
import com.aiot.domain.shared.VehicleId;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

public class SafetyAlertEvent {

    private final AlertId alertId;
    private final TripId tripId;
    private final DriverId driverId;
    private final VehicleId vehicleId;
    private final AlertType alertType;
    private final RiskLevel riskLevel;
    private final LocalDateTime occurredAt;
    private final GeoLocation location;
    private final PhysiologicalSnapshot physiologicalSnapshot;
    private final String alertMessage;
    private boolean resolved;
    private LocalDateTime resolvedAt;
    private final LocalDateTime createdAt;

    private SafetyAlertEvent(AlertId alertId, TripId tripId, DriverId driverId, VehicleId vehicleId,
                            AlertType alertType, RiskLevel riskLevel, LocalDateTime occurredAt,
                            GeoLocation location, PhysiologicalSnapshot physiologicalSnapshot,
                            String alertMessage) {
        this.alertId = alertId;
        this.tripId = tripId;
        this.driverId = driverId;
        this.vehicleId = vehicleId;
        this.alertType = alertType;
        this.riskLevel = riskLevel;
        this.occurredAt = occurredAt;
        this.location = location;
        this.physiologicalSnapshot = physiologicalSnapshot;
        this.alertMessage = alertMessage;
        this.resolved = false;
        this.resolvedAt = null;
        this.createdAt = LocalDateTime.now();
    }

    public static SafetyAlertEvent create(TripId tripId, DriverId driverId, VehicleId vehicleId,
                                          AlertType alertType, RiskLevel riskLevel, LocalDateTime occurredAt,
                                          GeoLocation location, PhysiologicalSnapshot physiologicalSnapshot,
                                          String alertMessage) {
        Objects.requireNonNull(tripId, "tripId must not be null");
        Objects.requireNonNull(driverId, "driverId must not be null");
        Objects.requireNonNull(vehicleId, "vehicleId must not be null");
        Objects.requireNonNull(alertType, "alertType must not be null");
        Objects.requireNonNull(riskLevel, "riskLevel must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(alertMessage, "alertMessage must not be null");

        return new SafetyAlertEvent(AlertId.generate(), tripId, driverId, vehicleId,
                alertType, riskLevel, occurredAt, location, physiologicalSnapshot, alertMessage);
    }

    public void resolve(LocalDateTime resolvedAt) {
        Objects.requireNonNull(resolvedAt, "resolvedAt must not be null");
        if (resolved) {
            throw new IllegalStateException("Alert already resolved");
        }
        this.resolved = true;
        this.resolvedAt = resolvedAt;
    }

    public void validate() {
        Objects.requireNonNull(alertId, "alertId must not be null");
        Objects.requireNonNull(tripId, "tripId must not be null");
        Objects.requireNonNull(driverId, "driverId must not be null");
        Objects.requireNonNull(vehicleId, "vehicleId must not be null");
        Objects.requireNonNull(alertType, "alertType must not be null");
        Objects.requireNonNull(riskLevel, "riskLevel must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(alertMessage, "alertMessage must not be null");
        if (alertMessage.isBlank()) {
            throw new IllegalStateException("alertMessage must not be blank");
        }
        if (resolved && resolvedAt == null) {
            throw new IllegalStateException("resolvedAt must be set when resolved is true");
        }
    }

    public AlertId alertId() { return alertId; }
    public TripId tripId() { return tripId; }
    public DriverId driverId() { return driverId; }
    public VehicleId vehicleId() { return vehicleId; }
    public AlertType alertType() { return alertType; }
    public RiskLevel riskLevel() { return riskLevel; }
    public LocalDateTime occurredAt() { return occurredAt; }
    public Optional<GeoLocation> location() { return Optional.ofNullable(location); }
    public Optional<PhysiologicalSnapshot> physiologicalSnapshot() { return Optional.ofNullable(physiologicalSnapshot); }
    public String alertMessage() { return alertMessage; }
    public boolean isResolved() { return resolved; }
    public Optional<LocalDateTime> resolvedAt() { return Optional.ofNullable(resolvedAt); }
    public LocalDateTime createdAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SafetyAlertEvent that = (SafetyAlertEvent) o;
        return Objects.equals(alertId, that.alertId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(alertId);
    }
}