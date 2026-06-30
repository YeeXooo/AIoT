package com.aiot.infra.persistence;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "t_trip_physiological_snapshot")
@IdClass(PhysiologicalSnapshotEntity.SnapshotId.class)
public class PhysiologicalSnapshotEntity {

    @Id
    @Column(name = "trip_id", length = 36)
    private String tripId;

    @Id
    private LocalDateTime timestamp;

    private Integer heartRate;
    private Double bloodOxygen;
    private Double emotionIndex;
    private Integer respiratoryRate;
    private Integer systolicBp;
    private Integer diastolicBp;
    private Double fatigueIndex;
    private Double bodyTemperature;

    @Column(length = 16)
    private String source;

    public String getTripId() { return tripId; }
    public void setTripId(String tripId) { this.tripId = tripId; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public Integer getHeartRate() { return heartRate; }
    public void setHeartRate(Integer heartRate) { this.heartRate = heartRate; }
    public Double getBloodOxygen() { return bloodOxygen; }
    public void setBloodOxygen(Double bloodOxygen) { this.bloodOxygen = bloodOxygen; }
    public Double getEmotionIndex() { return emotionIndex; }
    public void setEmotionIndex(Double emotionIndex) { this.emotionIndex = emotionIndex; }
    public Integer getRespiratoryRate() { return respiratoryRate; }
    public void setRespiratoryRate(Integer respiratoryRate) { this.respiratoryRate = respiratoryRate; }
    public Integer getSystolicBp() { return systolicBp; }
    public void setSystolicBp(Integer systolicBp) { this.systolicBp = systolicBp; }
    public Integer getDiastolicBp() { return diastolicBp; }
    public void setDiastolicBp(Integer diastolicBp) { this.diastolicBp = diastolicBp; }
    public Double getFatigueIndex() { return fatigueIndex; }
    public void setFatigueIndex(Double fatigueIndex) { this.fatigueIndex = fatigueIndex; }
    public Double getBodyTemperature() { return bodyTemperature; }
    public void setBodyTemperature(Double bodyTemperature) { this.bodyTemperature = bodyTemperature; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public static class SnapshotId implements Serializable {
        private String tripId;
        private LocalDateTime timestamp;
        public SnapshotId() {}
        public String getTripId() { return tripId; }
        public void setTripId(String tripId) { this.tripId = tripId; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof SnapshotId)) return false;
            SnapshotId that = (SnapshotId) o;
            return tripId.equals(that.tripId) && timestamp.equals(that.timestamp);
        }
        @Override public int hashCode() { return tripId.hashCode() + timestamp.hashCode(); }
    }
}
