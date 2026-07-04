package com.aiot.infra.repository;

import com.aiot.infra.persistence.VehicleJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleJpaRepository extends JpaRepository<VehicleJpaEntity, String> {

    List<VehicleJpaEntity> findByFleetId(String fleetId);

    Optional<VehicleJpaEntity> findByTerminalSn(String terminalSn);

    @Query("SELECT v FROM VehicleJpaEntity v WHERE v.licensePlate LIKE %?1%")
    List<VehicleJpaEntity> findByLicensePlateLike(String keyword);
}
