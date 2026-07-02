package com.aiot.application.risk;

import com.aiot.domain.model.SensorReading;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.VehicleId;

import java.time.LocalDateTime;
import java.util.List;

public interface IRiskMonitoringService {

    Result<StartMonitoringResponse, AppError> startMonitoringSession(DriverId driverId, VehicleId vehicleId);

    Result<Result.Unit, AppError> processSensorReading(String sessionHandle, SensorReading reading);

    Result<GetDriverRiskStatusResponse, AppError> getDriverRiskStatus(DriverId driverId);

    Result<QueryAlertHistoryResponse, AppError> queryAlertHistory(
            DriverId driverId, LocalDateTime from, LocalDateTime to,
            String alertType, String riskLevel, int page, int size);

    record StartMonitoringResponse(String sessionHandle, String driverId, String vehicleId, String status) { }

    record GetDriverRiskStatusResponse(String driverId, String statusColor, List<ActiveRiskInfo> activeRisks, LocalDateTime lastUpdated) { }

    record ActiveRiskInfo(String alertType, String riskLevel, String summary, LocalDateTime detectedAt) { }

    record QueryAlertHistoryResponse(List<AlertHistoryItem> items, int totalCount, int page, int size) { }

    record AlertHistoryItem(
            String alertId, String alertType, String riskLevel,
            String tripId, String driverId, String vehicleId,
            LocalDateTime occurredAt, String alertMsg
    ) { }
}
