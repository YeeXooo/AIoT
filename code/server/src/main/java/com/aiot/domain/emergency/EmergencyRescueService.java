package com.aiot.domain.emergency;

import com.aiot.domain.model.RescueAuthorizationToken;
import com.aiot.domain.shared.AccountId;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.RescueReportId;
import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.VehicleId;

import java.util.List;

public interface EmergencyRescueService {
    Result<RescueReport, AppError> createRescueReport(VehicleId vehicleId, DriverId driverId);
    Result<RescueAuthorizationToken, AppError> issueRescueAuthorization(RescueReportId reportId, List<String> operations, int validitySeconds);
    Result<Void, AppError> verifyAndConsumeToken(String token, String operation, VehicleId vehicleId);
    Result<Void, AppError> triggerManualRescue(DriverId driverId, AccountId requesterId);
    Result<List<RescueRecord>, AppError> queryRescueHistory(DriverId driverId);

    record RescueReport(String reportId, String vehicleId, String driverId, String location, String status) { }
    record RescueRecord(String recordId, String driverId, long timestamp, String status) { }
}
