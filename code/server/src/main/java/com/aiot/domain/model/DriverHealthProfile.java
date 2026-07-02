package com.aiot.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

public class DriverHealthProfile {

    private final String bloodType;
    private final String allergies;
    private final String chronicConditions;
    private final String medications;
    private final Double baselineHeartRate;
    private final Double baselineBloodPressureSystolic;
    private final Double baselineBloodPressureDiastolic;
    private final String emergencyContactName;
    private final String emergencyContactPhone;
    private final String medicalNotes;
    private final LocalDateTime lastUpdatedAt;

    private DriverHealthProfile(String bloodType, String allergies, String chronicConditions,
                                String medications, Double baselineHeartRate,
                                Double baselineBloodPressureSystolic, Double baselineBloodPressureDiastolic,
                                String emergencyContactName, String emergencyContactPhone, String medicalNotes) {
        this.bloodType = bloodType;
        this.allergies = allergies;
        this.chronicConditions = chronicConditions;
        this.medications = medications;
        this.baselineHeartRate = baselineHeartRate;
        this.baselineBloodPressureSystolic = baselineBloodPressureSystolic;
        this.baselineBloodPressureDiastolic = baselineBloodPressureDiastolic;
        this.emergencyContactName = emergencyContactName;
        this.emergencyContactPhone = emergencyContactPhone;
        this.medicalNotes = medicalNotes;
        this.lastUpdatedAt = LocalDateTime.now();
    }

    public static DriverHealthProfile create(String bloodType, String allergies, String chronicConditions,
                                             String medications, Double baselineHeartRate,
                                             Double baselineBloodPressureSystolic, Double baselineBloodPressureDiastolic,
                                             String emergencyContactName, String emergencyContactPhone, String medicalNotes) {
        return new DriverHealthProfile(bloodType, allergies, chronicConditions,
                medications, baselineHeartRate, baselineBloodPressureSystolic,
                baselineBloodPressureDiastolic, emergencyContactName, emergencyContactPhone, medicalNotes);
    }

    public static DriverHealthProfile empty() {
        return new DriverHealthProfile(null, null, null, null, null, null, null, null, null, null);
    }

    public DriverHealthProfile update(String bloodType, String allergies, String chronicConditions,
                                      String medications, Double baselineHeartRate,
                                      Double baselineBloodPressureSystolic, Double baselineBloodPressureDiastolic,
                                      String emergencyContactName, String emergencyContactPhone, String medicalNotes) {
        return new DriverHealthProfile(
                bloodType != null ? bloodType : this.bloodType,
                allergies != null ? allergies : this.allergies,
                chronicConditions != null ? chronicConditions : this.chronicConditions,
                medications != null ? medications : this.medications,
                baselineHeartRate != null ? baselineHeartRate : this.baselineHeartRate,
                baselineBloodPressureSystolic != null ? baselineBloodPressureSystolic : this.baselineBloodPressureSystolic,
                baselineBloodPressureDiastolic != null ? baselineBloodPressureDiastolic : this.baselineBloodPressureDiastolic,
                emergencyContactName != null ? emergencyContactName : this.emergencyContactName,
                emergencyContactPhone != null ? emergencyContactPhone : this.emergencyContactPhone,
                medicalNotes != null ? medicalNotes : this.medicalNotes
        );
    }

    public void validate() {
        if (baselineHeartRate != null && (baselineHeartRate < 30 || baselineHeartRate > 200)) {
            throw new IllegalStateException("baselineHeartRate must be between 30 and 200");
        }
        if (baselineBloodPressureSystolic != null && (baselineBloodPressureSystolic < 60 || baselineBloodPressureSystolic > 200)) {
            throw new IllegalStateException("baselineBloodPressureSystolic must be between 60 and 200");
        }
        if (baselineBloodPressureDiastolic != null && (baselineBloodPressureDiastolic < 40 || baselineBloodPressureDiastolic > 120)) {
            throw new IllegalStateException("baselineBloodPressureDiastolic must be between 40 and 120");
        }
        if (baselineBloodPressureSystolic != null && baselineBloodPressureDiastolic != null
                && baselineBloodPressureSystolic <= baselineBloodPressureDiastolic) {
            throw new IllegalStateException("baselineBloodPressureSystolic must be greater than baselineBloodPressureDiastolic");
        }
    }

    public Optional<String> bloodType() { return Optional.ofNullable(bloodType); }
    public Optional<String> allergies() { return Optional.ofNullable(allergies); }
    public Optional<String> chronicConditions() { return Optional.ofNullable(chronicConditions); }
    public Optional<String> medications() { return Optional.ofNullable(medications); }
    public Optional<Double> baselineHeartRate() { return Optional.ofNullable(baselineHeartRate); }
    public Optional<Double> baselineBloodPressureSystolic() { return Optional.ofNullable(baselineBloodPressureSystolic); }
    public Optional<Double> baselineBloodPressureDiastolic() { return Optional.ofNullable(baselineBloodPressureDiastolic); }
    public Optional<String> emergencyContactName() { return Optional.ofNullable(emergencyContactName); }
    public Optional<String> emergencyContactPhone() { return Optional.ofNullable(emergencyContactPhone); }
    public Optional<String> medicalNotes() { return Optional.ofNullable(medicalNotes); }
    public LocalDateTime lastUpdatedAt() { return lastUpdatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        DriverHealthProfile that = (DriverHealthProfile) o;
        return Objects.equals(bloodType, that.bloodType) &&
                Objects.equals(allergies, that.allergies) &&
                Objects.equals(chronicConditions, that.chronicConditions) &&
                Objects.equals(medications, that.medications) &&
                Objects.equals(baselineHeartRate, that.baselineHeartRate) &&
                Objects.equals(baselineBloodPressureSystolic, that.baselineBloodPressureSystolic) &&
                Objects.equals(baselineBloodPressureDiastolic, that.baselineBloodPressureDiastolic) &&
                Objects.equals(emergencyContactName, that.emergencyContactName) &&
                Objects.equals(emergencyContactPhone, that.emergencyContactPhone) &&
                Objects.equals(medicalNotes, that.medicalNotes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bloodType, allergies, chronicConditions, medications,
                baselineHeartRate, baselineBloodPressureSystolic, baselineBloodPressureDiastolic,
                emergencyContactName, emergencyContactPhone, medicalNotes);
    }
}