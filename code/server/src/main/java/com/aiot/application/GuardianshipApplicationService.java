package com.aiot.application;

import com.aiot.infra.persistence.GuardianshipEntity;
import com.aiot.infra.repository.GuardianshipJpaRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class GuardianshipApplicationService {

    private final GuardianshipJpaRepository repo;

    public GuardianshipApplicationService(GuardianshipJpaRepository repo) {
        this.repo = repo;
    }

    public List<GuardianshipEntity> findByDriver(String driverId) {
        return repo.findActiveByDriver(driverId);
    }

    public List<GuardianshipEntity> findAll() {
        return repo.findAll();
    }

    public GuardianshipEntity create(GuardianshipEntity entity) {
        entity.setGrantedAt(LocalDateTime.now());
        return repo.save(entity);
    }

    public void revoke(String driverId, String accountId) {
        repo.findActive(driverId, accountId).ifPresent(g -> {
            g.setRevokedAt(LocalDateTime.now());
            repo.save(g);
        });
    }
}
