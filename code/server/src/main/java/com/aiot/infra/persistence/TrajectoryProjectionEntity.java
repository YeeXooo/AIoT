package com.aiot.infra.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "t_trajectory_projection")
public class TrajectoryProjectionEntity {

    @Id
    @Column(name = "trajectory_id", length = 36)
    private String trajectoryId;

    @Column(name = "trip_id", nullable = false, length = 36)
    private String tripId;

    @Column(name = "vehicle_id", nullable = false, length = 36)
    private String vehicleId;

    @Column(name = "driver_id", nullable = false, length = 36)
    private String driverId;

    private Double gpsLatitude;
    private Double gpsLongitude;
    private Double speed;

    @Column(nullable = false)
    private LocalDateTime recordedAt;

    public String getTrajectoryId() { return trajectoryId; }
    public void setTrajectoryId(String trajectoryId) { this.trajectoryId = trajectoryId; }
    public String getTripId() { return tripId; }
    public void setTripId(String tripId) { this.tripId = tripId; }
    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }
    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }
    public Double getGpsLatitude() { return gpsLatitude; }
    public void setGpsLatitude(Double gpsLatitude) { this.gpsLatitude = gpsLatitude; }
    public Double getGpsLongitude() { return gpsLongitude; }
    public void setGpsLongitude(Double gpsLongitude) { this.gpsLongitude = gpsLongitude; }
    public Double getSpeed() { return speed; }
    public void setSpeed(Double speed) { this.speed = speed; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }
}
