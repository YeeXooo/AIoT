package com.aiot.infra.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "t_driver")
public class DriverJpaEntity {

    @Id
    @Column(name = "driver_id", length = 36)
    private String driverId;

    @Version
    private Integer version;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 32)
    private String phone;

    private Integer comprehensiveScore;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }
    public Integer getVersion() { return version; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Integer getComprehensiveScore() { return comprehensiveScore; }
    public void setComprehensiveScore(Integer comprehensiveScore) { this.comprehensiveScore = comprehensiveScore; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
