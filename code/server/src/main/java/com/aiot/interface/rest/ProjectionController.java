package com.aiot.interfaces.rest;

import com.aiot.infra.persistence.*;
import com.aiot.infra.repository.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projection")
public class ProjectionController {

    private final AlertProjectionJpaRepository alertProjRepo;
    private final FleetDashboardProjectionJpaRepository fleetProjRepo;
    private final TrajectoryProjectionJpaRepository trajProjRepo;
    private final DomainEventOutboxJpaRepository outboxRepo;
    private final DomainEventDlqJpaRepository dlqRepo;

    public ProjectionController(AlertProjectionJpaRepository alertProjRepo,
                                 FleetDashboardProjectionJpaRepository fleetProjRepo,
                                 TrajectoryProjectionJpaRepository trajProjRepo,
                                 DomainEventOutboxJpaRepository outboxRepo,
                                 DomainEventDlqJpaRepository dlqRepo) {
        this.alertProjRepo = alertProjRepo;
        this.fleetProjRepo = fleetProjRepo;
        this.trajProjRepo = trajProjRepo;
        this.outboxRepo = outboxRepo;
        this.dlqRepo = dlqRepo;
    }

    @GetMapping("/alert")
    public List<AlertProjectionEntity> alerts(@RequestParam(required = false) String fleetId,
                                               @RequestParam(required = false) String riskLevel,
                                               @RequestParam(required = false) Boolean activeOnly) {
        if (activeOnly != null && activeOnly) return alertProjRepo.findByResolvedAtIsNull();
        if (fleetId != null) return alertProjRepo.findByFleetId(fleetId);
        if (riskLevel != null) return alertProjRepo.findByRiskLevel(riskLevel);
        return alertProjRepo.findAll();
    }

    @GetMapping("/dashboard")
    public List<FleetDashboardProjectionEntity> dashboard(@RequestParam(required = false) String fleetId) {
        if (fleetId != null) return fleetProjRepo.findByFleetId(fleetId);
        return fleetProjRepo.findAll();
    }

    @GetMapping("/trajectory")
    public List<TrajectoryProjectionEntity> trajectory(@RequestParam String tripId) {
        return trajProjRepo.findByTripIdOrderByRecordedAtAsc(tripId);
    }

    @GetMapping("/outbox/pending")
    public List<DomainEventOutboxEntity> outboxPending() {
        return outboxRepo.findUnpublished();
    }

    @GetMapping("/dlq/list")
    public List<DomainEventDlqEntity> dlqList() {
        return dlqRepo.findAll();
    }
}
