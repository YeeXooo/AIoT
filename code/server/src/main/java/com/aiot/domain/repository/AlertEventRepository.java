package com.aiot.domain.repository;

import com.aiot.domain.model.SafetyAlertEvent;

import java.util.List;
import java.util.Optional;

public interface AlertEventRepository {
    void save(SafetyAlertEvent alert);
    Optional<SafetyAlertEvent> findById(String id);
    List<SafetyAlertEvent> findByDriverId(String driverId);
    List<SafetyAlertEvent> findByRiskLevel(String riskLevel);
    List<SafetyAlertEvent> findFiltered(String driverId, String riskLevel, String alertType);
    List<SafetyAlertEvent> findAll();
    void delete(String id);
}
