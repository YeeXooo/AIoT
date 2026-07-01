package com.aiot.domain.model;

import java.time.LocalDateTime;

public class Driver {
    private String driverId;
    private String name;
    private String phone;
    private Integer comprehensiveScore;
    private LocalDateTime createdAt;

    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Integer getComprehensiveScore() { return comprehensiveScore; }
    public void setComprehensiveScore(Integer comprehensiveScore) { this.comprehensiveScore = comprehensiveScore; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
