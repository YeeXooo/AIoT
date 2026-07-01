package com.aiot.infra.repository;

import com.aiot.infra.persistence.GuardianshipEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GuardianshipJpaRepository extends JpaRepository<GuardianshipEntity, String> {

    List<GuardianshipEntity> findByDriverId(String driverId);

    List<GuardianshipEntity> findByAccountId(String accountId);

    @Query("SELECT g FROM GuardianshipEntity g WHERE g.driverId = ?1 AND g.accountId = ?2 AND g.revokedAt IS NULL")
    Optional<GuardianshipEntity> findActive(String driverId, String accountId);

    @Query("SELECT g FROM GuardianshipEntity g WHERE g.driverId = ?1 AND g.revokedAt IS NULL")
    List<GuardianshipEntity> findActiveByDriver(String driverId);
}
