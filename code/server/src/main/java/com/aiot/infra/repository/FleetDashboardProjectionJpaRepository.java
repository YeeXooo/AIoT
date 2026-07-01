package com.aiot.infra.repository;

import com.aiot.infra.persistence.FleetDashboardProjectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FleetDashboardProjectionJpaRepository extends JpaRepository<FleetDashboardProjectionEntity, String> {

    List<FleetDashboardProjectionEntity> findByFleetId(String fleetId);
}
