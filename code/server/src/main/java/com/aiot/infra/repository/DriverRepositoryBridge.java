package com.aiot.infra.repository;

import com.aiot.domain.model.Driver;
import com.aiot.domain.repository.DriverRepository;
import com.aiot.domain.shared.DriverId;
import com.aiot.infra.persistence.DriverJpaEntity;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@org.springframework.stereotype.Repository
public class DriverRepositoryBridge implements DriverRepository {

    private final DriverJpaRepository jpaRepository;

    public DriverRepositoryBridge(DriverJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Driver driver) {
        DriverJpaEntity entity = new DriverJpaEntity();
        entity.setDriverId(driver.driverId().id());
        entity.setName(driver.name());
        entity.setPhone(driver.phone());
        entity.setComprehensiveScore(
                driver.comprehensiveScore().map(score -> score.getValue()).orElse(null));
        jpaRepository.save(entity);
    }

    @Override
    public Optional<Driver> findById(String id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Driver> findByNameLike(String keyword) {
        return jpaRepository.findByNameLike(keyword).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Driver> findAll() {
        return jpaRepository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String id) {
        jpaRepository.deleteById(id);
    }

    private Driver toDomain(DriverJpaEntity entity) {
        return Driver.reconstitute(
                new DriverId(entity.getDriverId()),
                entity.getName(),
                entity.getPhone(),
                entity.getComprehensiveScore(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
