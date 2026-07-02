package com.aiot.domain.model;

import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.TripId;
import com.aiot.domain.shared.VehicleId;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class Trip {

    private final TripId tripId;
    private final DriverId driverId;
    private final VehicleId vehicleId;
    private final LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private final List<PhysiologicalSnapshot> physiologicalSnapshots;
    private DrivingBehaviorCounters drivingBehaviorCounters;
    private TripScore tripScore;
    private L3DurationTracker l3DurationTracker;
    private Integer version;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Trip(TripId tripId, DriverId driverId, VehicleId vehicleId, LocalDateTime startedAt) {
        this.tripId = tripId;
        this.driverId = driverId;
        this.vehicleId = vehicleId;
        this.startedAt = startedAt;
        this.endedAt = null;
        this.physiologicalSnapshots = new ArrayList<>();
        this.drivingBehaviorCounters = DrivingBehaviorCounters.init();
        this.tripScore = null;
        this.l3DurationTracker = null;
        this.version = 1;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public static Trip reconstitute(TripId tripId, DriverId driverId, VehicleId vehicleId,
                                     LocalDateTime startedAt, LocalDateTime endedAt,
                                     Integer hardBrakingCount, Integer hardAccelerationCount,
                                     Integer scoreValue, Integer version,
                                     LocalDateTime createdAt, LocalDateTime updatedAt) {
        Trip t = new Trip(tripId, driverId, vehicleId, startedAt);
        t.endedAt = endedAt;
        t.drivingBehaviorCounters = DrivingBehaviorCounters.of(
                hardBrakingCount != null ? hardBrakingCount : 0,
                hardAccelerationCount != null ? hardAccelerationCount : 0);
        if (scoreValue != null) {
            t.tripScore = TripScore.of(scoreValue);
        }
        t.version = version;
        return t;
    }

    public static Trip start(DriverId driverId, VehicleId vehicleId, LocalDateTime startedAt) {
        Objects.requireNonNull(driverId, "driverId must not be null");
        Objects.requireNonNull(vehicleId, "vehicleId must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");

        return new Trip(TripId.generate(), driverId, vehicleId, startedAt);
    }

    public void end(LocalDateTime endedAt) {
        Objects.requireNonNull(endedAt, "endedAt must not be null");
        if (endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("endedAt must be after startedAt");
        }
        this.endedAt = endedAt;
        this.updatedAt = LocalDateTime.now();
    }

    public void addPhysiologicalSnapshot(PhysiologicalSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        if (isEnded()) {
            throw new IllegalStateException("Cannot add snapshot to ended trip");
        }
        this.physiologicalSnapshots.add(snapshot);
        this.updatedAt = LocalDateTime.now();
    }

    public void updateDrivingBehaviorCounters(DrivingBehaviorCounters counters) {
        Objects.requireNonNull(counters, "counters must not be null");
        this.drivingBehaviorCounters = counters;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateTripScore(TripScore tripScore) {
        Objects.requireNonNull(tripScore, "tripScore must not be null");
        if (!isEnded()) {
            throw new IllegalStateException("Cannot set trip score before trip ends");
        }
        this.tripScore = tripScore;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateL3DurationTracker(L3DurationTracker tracker) {
        this.l3DurationTracker = tracker;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isEnded() {
        return endedAt != null;
    }

    public void validate() {
        Objects.requireNonNull(tripId, "tripId must not be null");
        Objects.requireNonNull(driverId, "driverId must not be null");
        Objects.requireNonNull(vehicleId, "vehicleId must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        if (endedAt != null && endedAt.isBefore(startedAt)) {
            throw new IllegalStateException("endedAt must be after startedAt");
        }
        if (drivingBehaviorCounters == null) {
            drivingBehaviorCounters = DrivingBehaviorCounters.init();
        }
    }

    public TripId tripId() { return tripId; }
    public DriverId driverId() { return driverId; }
    public VehicleId vehicleId() { return vehicleId; }
    public LocalDateTime startedAt() { return startedAt; }
    public Optional<LocalDateTime> endedAt() { return Optional.ofNullable(endedAt); }
    public List<PhysiologicalSnapshot> physiologicalSnapshots() { return List.copyOf(physiologicalSnapshots); }
    public DrivingBehaviorCounters drivingBehaviorCounters() { return drivingBehaviorCounters; }
    public Optional<TripScore> tripScore() { return Optional.ofNullable(tripScore); }
    public Optional<L3DurationTracker> l3DurationTracker() { return Optional.ofNullable(l3DurationTracker); }
    public Integer version() { return version; }
    public void version(Integer version) { this.version = version; }
    public LocalDateTime createdAt() { return createdAt; }
    public LocalDateTime updatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        Trip trip = (Trip) o;
        return Objects.equals(tripId, trip.tripId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(tripId);
    }
}