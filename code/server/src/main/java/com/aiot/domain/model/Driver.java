package com.aiot.domain.model;

import com.aiot.domain.shared.DriverId;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

public class Driver {

    private final DriverId driverId;
    private String name;
    private String phone;
    private byte[] faceFeatureVector;
    private DriverComprehensiveScore comprehensiveScore;
    private DriverHealthProfile healthProfile;
    private Integer version;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean active;

    private Driver(DriverId driverId, String name, String phone) {
        this.driverId = driverId;
        this.name = name;
        this.phone = phone;
        this.faceFeatureVector = null;
        this.comprehensiveScore = null;
        this.healthProfile = null;
        this.version = 1;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.active = true;
    }

    public static Driver create(String name, String phone) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(phone, "phone must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (phone.isBlank()) {
            throw new IllegalArgumentException("phone must not be blank");
        }

        return new Driver(DriverId.generate(), name, phone);
    }

    public void updateName(String name) {
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        this.name = name;
        this.updatedAt = LocalDateTime.now();
    }

    public void updatePhone(String phone) {
        Objects.requireNonNull(phone, "phone must not be null");
        if (phone.isBlank()) {
            throw new IllegalArgumentException("phone must not be blank");
        }
        this.phone = phone;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateFaceFeatureVector(byte[] faceFeatureVector) {
        this.faceFeatureVector = faceFeatureVector;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateComprehensiveScore(DriverComprehensiveScore score) {
        Objects.requireNonNull(score, "score must not be null");
        this.comprehensiveScore = score;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateHealthProfile(DriverHealthProfile healthProfile) {
        Objects.requireNonNull(healthProfile, "healthProfile must not be null");
        this.healthProfile = healthProfile;
        this.updatedAt = LocalDateTime.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = LocalDateTime.now();
    }

    public void validate() {
        Objects.requireNonNull(driverId, "driverId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(phone, "phone must not be null");
        if (name.isBlank()) {
            throw new IllegalStateException("name must not be blank");
        }
        if (phone.isBlank()) {
            throw new IllegalStateException("phone must not be blank");
        }
    }

    public DriverId driverId() { return driverId; }
    public String name() { return name; }
    public String phone() { return phone; }
    public Optional<byte[]> faceFeatureVector() { return Optional.ofNullable(faceFeatureVector); }
    public Optional<DriverComprehensiveScore> comprehensiveScore() { return Optional.ofNullable(comprehensiveScore); }
    public Optional<DriverHealthProfile> healthProfile() { return Optional.ofNullable(healthProfile); }
    public Integer version() { return version; }
    public void version(Integer version) { this.version = version; }
    public LocalDateTime createdAt() { return createdAt; }
    public LocalDateTime updatedAt() { return updatedAt; }
    public boolean isActive() { return active; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Driver driver = (Driver) o;
        return Objects.equals(driverId, driver.driverId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(driverId);
    }
}