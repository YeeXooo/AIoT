package com.aiot.domain.fleet;

import com.aiot.domain.model.Vehicle;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;

import java.util.List;

public interface ReportGenerationService {
    Result<FleetReport, AppError> generateFleetReport(String fleetId);

    Result<DriverReport, AppError> generateDriverReport(String driverId);

    record FleetReport(String fleetId, int vehicleCount, int driverCount, double avgScore, String summary) {}

    record DriverReport(String driverId, String driverName, double score, int tripCount, String summary) {}
}
