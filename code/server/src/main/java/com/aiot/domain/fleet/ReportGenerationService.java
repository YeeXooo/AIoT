package com.aiot.domain.fleet;

import com.aiot.domain.model.TimeRange;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.Result;

public interface ReportGenerationService {
    Result<FleetReport, AppError> generateFleetReport(String fleetId);

    Result<DriverReport, AppError> generateDriverReport(DriverId driverId, TimeRange timeRange);

    record FleetReport(String fleetId, int vehicleCount, int driverCount, double avgScore, String summary) { }

    record DriverReport(String driverId, String driverName, double score, int tripCount, String summary) { }
}
