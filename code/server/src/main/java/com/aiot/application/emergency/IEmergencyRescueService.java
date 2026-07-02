package com.aiot.application.emergency;

import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.VehicleId;

import java.util.List;

public interface IEmergencyRescueService {
    Result<Void, AppError> confirmSOSReport(String rescueReportId, String ackToken);
    Result<IssueRescueTokenResponse, AppError> issueRescueToken(String rescueReportId, List<String> operations, int validitySeconds);
    Result<VerifyTokenResponse, AppError> verifyRescueToken(String token, String operation, VehicleId vehicleId);
    Result<RescueHistoryResponse, AppError> queryRescueHistory(DriverId driverId, int page, int size);
    Result<CreateRescueReportResponse, AppError> createRescueReport(DriverId driverId, VehicleId vehicleId);

    record IssueRescueTokenResponse(String token, long expiresAt) { }
    record VerifyTokenResponse(boolean valid, String operation) { }
    record RescueHistoryResponse(List<RescueRecordItem> records, long totalCount) { }
    record RescueRecordItem(String recordId, String driverId, long timestamp, String status) { }
    record CreateRescueReportResponse(String reportId, String status) { }
}
