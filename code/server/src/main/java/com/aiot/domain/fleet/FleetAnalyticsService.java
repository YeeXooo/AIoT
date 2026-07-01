package com.aiot.domain.fleet;

import com.aiot.domain.model.Trip;
import com.aiot.domain.repository.TripRepository;
import com.aiot.domain.repository.AlertEventRepository;
import com.aiot.domain.model.AlertEvent;
import com.aiot.domain.shared.PerformanceWarningEvent;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FleetAnalyticsService {

    private final TripRepository tripRepo;
    private final AlertEventRepository alertRepo;

    public FleetAnalyticsService(TripRepository tripRepo, AlertEventRepository alertRepo) {
        this.tripRepo = tripRepo;
        this.alertRepo = alertRepo;
    }

    public Map<String, Integer> getFleetStats() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("totalTrips", tripRepo.findAll().size());
        stats.put("activeTrips", tripRepo.findActiveTrips().size());
        stats.put("totalAlerts", alertRepo.findAll().size());
        return stats;
    }
}
