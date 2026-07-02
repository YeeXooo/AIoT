package com.aiot.application.fleet;

import com.aiot.domain.model.TimeRange;
import com.aiot.domain.shared.AccountId;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.VehicleId;

import java.util.List;
import java.util.Map;

public interface IFleetManagementService {
    Result<GetFatigueDistributionResponse, AppError> getFatigueDistribution(String fleetId);
    Result<GetOfflineVehiclesResponse, AppError> getOfflineVehicles(String fleetId);
    Result<DrillDownResponse, AppError> drillDownHighRisk(String fleetId, String riskLevel, int page, int size);
    Result<GenerateReportResponse, AppError> generateReport(DriverId driverId, TimeRange timeRange, String reportType);
    Result<SubscribeResponse, AppError> subscribePerformanceWarning(AccountId adminId, String fleetId);
    Result<TrajectoryResponse, AppError> queryVehicleTrajectory(VehicleId vehicleId, TimeRange timeRange, int page, int size);

    record GetFatigueDistributionResponse(Map<String, Double> distribution, String dataFreshness) {}
    record GetOfflineVehiclesResponse(List<OfflineVehicleSummary> vehicles) {}
    record OfflineVehicleSummary(String vehicleId, String licensePlate, String driverId, String reason, long offlineSince) {}
    record DrillDownResponse(List<HighRiskDriverSummary> drivers, long totalCount) {}
    record HighRiskDriverSummary(String driverId, String driverName, double score, String latestSummary, List<String> penaltyItems) {}
    record GenerateReportResponse(String reportId, Object reportData, String downloadUrl, boolean isEmpty) {}
    record SubscribeResponse(String subscriptionId) {}
    record TrajectoryResponse(List<TrajectoryPoint> points, long totalCount) {}
    record TrajectoryPoint(long timestamp, double lat, double lng, double speed) {}
}
