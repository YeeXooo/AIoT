package com.aiot.infra.repository;

import com.aiot.domain.event.AlertType;
import com.aiot.domain.event.RiskLevel;
import com.aiot.domain.model.SafetyAlertEvent;
import com.aiot.domain.repository.AlertEventRepository;
import com.aiot.domain.shared.AlertId;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.TripId;
import com.aiot.domain.shared.VehicleId;
import com.aiot.infra.persistence.AlertEventJpaEntity;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@org.springframework.stereotype.Repository
public class AlertEventRepositoryBridge implements AlertEventRepository {

    private final AlertEventJpaRepository jpaRepository;

    public AlertEventRepositoryBridge(AlertEventJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(SafetyAlertEvent alert) {
        AlertEventJpaEntity entity = new AlertEventJpaEntity();
        entity.setAlertId(alert.alertId().id());
        entity.setTripId(alert.tripId().id());
        entity.setDriverId(alert.driverId().id());
        entity.setVehicleId(alert.vehicleId().id());
        entity.setAlertType(alert.alertType().name());
        entity.setRiskLevel(alert.riskLevel().name());
        entity.setOccurredAt(alert.occurredAt());
        entity.setAlertMsg(alert.alertMessage());
        jpaRepository.save(entity);
    }

    @Override
    public Optional<SafetyAlertEvent> findById(String id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<SafetyAlertEvent> findByDriverId(String driverId) {
        return jpaRepository.findByDriverId(driverId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<SafetyAlertEvent> findByRiskLevel(String riskLevel) {
        return jpaRepository.findByRiskLevel(riskLevel).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<SafetyAlertEvent> findFiltered(String driverId, String riskLevel, String alertType) {
        return jpaRepository.findFiltered(driverId, riskLevel, alertType).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<SafetyAlertEvent> findAll() {
        return jpaRepository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String id) {
        jpaRepository.deleteById(id);
    }

    private SafetyAlertEvent toDomain(AlertEventJpaEntity entity) {
        return SafetyAlertEvent.reconstitute(
                new AlertId(entity.getAlertId()),
                new TripId(entity.getTripId()),
                new DriverId(entity.getDriverId()),
                new VehicleId(entity.getVehicleId()),
                AlertType.valueOf(entity.getAlertType()),
                RiskLevel.valueOf(entity.getRiskLevel()),
                entity.getOccurredAt(),
                entity.getAlertMsg(),
                false,
                null);
    }
}
