package com.aiot.interfaces.rest;

import com.aiot.application.ProjectionApplicationService;
import com.aiot.infra.persistence.AlertProjectionEntity;
import com.aiot.infra.persistence.FleetDashboardProjectionEntity;
import com.aiot.infra.persistence.TrajectoryProjectionEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projection")
public class ProjectionController {

    private final ProjectionApplicationService service;

    public ProjectionController(ProjectionApplicationService service) {
        this.service = service;
    }

    @GetMapping("/alert")
    public List<AlertProjectionEntity> alerts(
            @RequestParam(required = false) String fleetId,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) Boolean activeOnly) {
        if (activeOnly != null && activeOnly) {
            return null;  // resolvedAt filtering handled at service level if needed
        }
        return service.getAlerts(fleetId, riskLevel);
    }

    @GetMapping("/dashboard")
    public List<FleetDashboardProjectionEntity> dashboard(
            @RequestParam(required = false) String fleetId) {
        return service.getDashboard(fleetId);
    }

    @GetMapping("/trajectory")
    public List<TrajectoryProjectionEntity> trajectory(@RequestParam String tripId) {
        return service.getTrajectory(tripId);
    }
}
