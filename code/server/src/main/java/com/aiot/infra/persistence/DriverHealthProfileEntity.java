package com.aiot.infra.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "t_driver_health_profile")
public class DriverHealthProfileEntity {

    @Id
    @Column(name = "driver_id", length = 36)
    private String driverId;

    @Column(length = 8)
    private String bloodType;

    @Column(columnDefinition = "TEXT")
    private String allergyHistory;

    @Column(columnDefinition = "TEXT")
    private String chronicHistory;

    @Column(columnDefinition = "TEXT")
    private String medicationHistory;

    @Column(columnDefinition = "TEXT")
    private String baselineVitals;

    @Column(columnDefinition = "TEXT")
    private String emergencyContact;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }

    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }
    public String getBloodType() { return bloodType; }
    public void setBloodType(String bloodType) { this.bloodType = bloodType; }
    public String getAllergyHistory() { return allergyHistory; }
    public void setAllergyHistory(String allergyHistory) { this.allergyHistory = allergyHistory; }
    public String getChronicHistory() { return chronicHistory; }
    public void setChronicHistory(String chronicHistory) { this.chronicHistory = chronicHistory; }
    public String getMedicationHistory() { return medicationHistory; }
    public void setMedicationHistory(String medicationHistory) { this.medicationHistory = medicationHistory; }
    public String getBaselineVitals() { return baselineVitals; }
    public void setBaselineVitals(String baselineVitals) { this.baselineVitals = baselineVitals; }
    public String getEmergencyContact() { return emergencyContact; }
    public void setEmergencyContact(String emergencyContact) { this.emergencyContact = emergencyContact; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
