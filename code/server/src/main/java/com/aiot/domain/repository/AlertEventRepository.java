package com.aiot.domain.repository;

import com.aiot.domain.model.AlertEvent;

import java.util.List;
import java.util.Optional;

public interface AlertEventRepository {
    void save(AlertEvent alert);
    Optional<AlertEvent> findById(String id);
    List<AlertEvent> findByDriverId(String driverId);
    List<AlertEvent> findByRiskLevel(String riskLevel);
    List<AlertEvent> findFiltered(String driverId, String riskLevel, String alertType);
    List<AlertEvent> findAll();
    void delete(String id);
}
