package com.aiot.infra.repository;

import com.aiot.domain.model.Vehicle;
import com.aiot.domain.repository.VehicleRepository;
import com.aiot.domain.shared.VehicleId;
import com.aiot.infra.persistence.VehicleJpaEntity;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@org.springframework.stereotype.Repository
public class VehicleRepositoryBridge implements VehicleRepository {

    private final VehicleJpaRepository jpaRepository;

    public VehicleRepositoryBridge(VehicleJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Vehicle vehicle) {
        VehicleJpaEntity entity = new VehicleJpaEntity();
        entity.setVehicleId(vehicle.vehicleId().id());
        entity.setLicensePlate(vehicle.licensePlate());
        entity.setVin(vehicle.vin());
        entity.setTerminalSn(vehicle.terminalSn());
        entity.setFleetId(vehicle.fleetId().orElse(null));
        entity.setFirmwareVersion(vehicle.firmwareVersion().getVersionNumber());
        entity.setSensorStatus("OK");
        jpaRepository.save(entity);
    }

    @Override
    public Optional<Vehicle> findById(String id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Vehicle> findByFleetId(String fleetId) {
        return jpaRepository.findByFleetId(fleetId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Vehicle> findByLicensePlateLike(String keyword) {
        return jpaRepository.findByLicensePlateLike(keyword).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Vehicle> findAll() {
        return jpaRepository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String id) {
        jpaRepository.deleteById(id);
    }

    private Vehicle toDomain(VehicleJpaEntity entity) {
        return Vehicle.reconstitute(
                new VehicleId(entity.getVehicleId()),
                entity.getLicensePlate(),
                entity.getVin(),
                entity.getTerminalSn(),
                entity.getFleetId(),
                entity.getFirmwareVersion(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
