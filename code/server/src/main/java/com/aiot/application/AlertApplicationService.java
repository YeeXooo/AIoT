package com.aiot.application;

import com.aiot.domain.model.AlertEvent;
import com.aiot.domain.repository.AlertEventRepository;

import java.util.List;

public class AlertApplicationService {

    private final AlertEventRepository alertEventRepository;

    public AlertApplicationService(AlertEventRepository alertEventRepository) {
        this.alertEventRepository = alertEventRepository;
    }

    public List<AlertEvent> listAlerts(String driverId, String riskLevel, String alertType) {
        return alertEventRepository.findFiltered(
                driverId == null || driverId.isEmpty() ? null : driverId,
                riskLevel == null || riskLevel.isEmpty() ? null : riskLevel,
                alertType == null || alertType.isEmpty() ? null : alertType
        );
    }
}
