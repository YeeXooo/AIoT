package com.aiot.domain.fleet;

import com.aiot.domain.event.RiskLevel;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;

import java.util.List;
import java.util.Map;

public interface FleetAnalyticsService {
    Result<FatigueDistribution, AppError> getFleetFatigueDistribution(String fleetId);

    Result<List<DriverSummary>, AppError> drillDown(RiskLevel riskLevel);

    record FatigueDistribution(Map<String, Double> distribution) {}

    record DriverSummary(String driverId, double score, String summary) {}
}
