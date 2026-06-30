package com.aiot.infra.repository;

import com.aiot.infra.persistence.AlertProjectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertProjectionJpaRepository extends JpaRepository<AlertProjectionEntity, String> {

    List<AlertProjectionEntity> findByDriverId(String driverId);

    List<AlertProjectionEntity> findByRiskLevel(String riskLevel);

    List<AlertProjectionEntity> findByFleetId(String fleetId);

    List<AlertProjectionEntity> findByResolvedAtIsNull();
}
