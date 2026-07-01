package com.aiot.application;

import com.aiot.infra.persistence.DriverHealthProfileEntity;
import com.aiot.infra.repository.DriverHealthProfileJpaRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class HealthApplicationService {

    private final DriverHealthProfileJpaRepository repo;

    public HealthApplicationService(DriverHealthProfileJpaRepository repo) {
        this.repo = repo;
    }

    public Optional<DriverHealthProfileEntity> get(String driverId) {
        return repo.findById(driverId);
    }

    public DriverHealthProfileEntity save(DriverHealthProfileEntity profile) {
        return repo.save(profile);
    }
}
