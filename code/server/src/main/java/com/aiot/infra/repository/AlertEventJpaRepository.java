package com.aiot.infra.repository;

import com.aiot.infra.persistence.AlertEventJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertEventJpaRepository extends JpaRepository<AlertEventJpaEntity, String> {

    List<AlertEventJpaEntity> findByDriverId(String driverId);

    List<AlertEventJpaEntity> findByRiskLevel(String riskLevel);

    @Query("SELECT a FROM AlertEventJpaEntity a WHERE " +
           "(:driverId IS NULL OR a.driverId = :driverId) AND " +
           "(:riskLevel IS NULL OR a.riskLevel = :riskLevel) AND " +
           "(:alertType IS NULL OR a.alertType = :alertType)")
    List<AlertEventJpaEntity> findFiltered(@Param("driverId") String driverId,
                                           @Param("riskLevel") String riskLevel,
                                           @Param("alertType") String alertType);
}
