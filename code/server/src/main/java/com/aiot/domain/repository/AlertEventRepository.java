package com.aiot.domain.repository;

import com.aiot.domain.shared.AggregateId;

import java.util.List;
import java.util.Optional;

public interface AlertEventRepository {
    AlertEventRepository save(AlertEvent alert);
    Optional<AlertEvent> findById(AggregateId id);
    List<AlertEvent> findByDriverId(AggregateId driverId);
    List<AlertEvent> findByRiskLevel(String riskLevel);
    List<AlertEvent> findFiltered(AggregateId driverId, String riskLevel, String alertType);
    List<AlertEvent> findAll();
    void delete(AggregateId id);

    interface AlertEvent {
        AggregateId getId();
        AggregateId getTripId();
        AggregateId getDriverId();
        AggregateId getVehicleId();
        String getAlertType();
        String getRiskLevel();
        String getAlertMsg();
        java.time.LocalDateTime getOccurredAt();
    }
}
