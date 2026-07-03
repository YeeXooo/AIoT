package com.aiot.application;

import com.aiot.infra.persistence.AlertProjectionEntity;
import com.aiot.infra.persistence.FleetDashboardProjectionEntity;
import com.aiot.infra.persistence.TrajectoryProjectionEntity;
import com.aiot.infra.repository.AlertProjectionJpaRepository;
import com.aiot.infra.repository.FleetDashboardProjectionJpaRepository;
import com.aiot.infra.repository.TrajectoryProjectionJpaRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ProjectionApplicationService {
    private final AlertProjectionJpaRepository alertRepo;
    private final FleetDashboardProjectionJpaRepository fleetRepo;
    private final TrajectoryProjectionJpaRepository trajRepo;
    public ProjectionApplicationService(AlertProjectionJpaRepository alertRepo,
                                         FleetDashboardProjectionJpaRepository fleetRepo,
                                         TrajectoryProjectionJpaRepository trajRepo) {
        this.alertRepo = alertRepo;
        this.fleetRepo = fleetRepo;
        this.trajRepo = trajRepo;
    }
    public List<AlertProjectionEntity> getAlerts(String fleetId, String riskLevel) {
        if (fleetId != null && !fleetId.isEmpty()) {
            return alertRepo.findByFleetId(fleetId);
        }
        if (riskLevel != null && !riskLevel.isEmpty()) {
            return alertRepo.findByRiskLevel(riskLevel);
        }
        return alertRepo.findAll();
    }
    public List<FleetDashboardProjectionEntity> getDashboard(String fleetId) {
        if (fleetId != null && !fleetId.isEmpty()) {
            return fleetRepo.findByFleetId(fleetId);
        }
        return fleetRepo.findAll();
    }
    public List<TrajectoryProjectionEntity> getTrajectory(String tripId) {
        return trajRepo.findByTripIdOrderByRecordedAtAsc(tripId);
    }
}
