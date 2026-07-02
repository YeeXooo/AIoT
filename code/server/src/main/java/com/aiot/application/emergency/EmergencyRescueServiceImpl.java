package com.aiot.application.emergency;

import com.aiot.domain.emergency.EmergencyRescueService;
import com.aiot.domain.model.RescueAuthorizationToken;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.RescueReportId;
import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.VehicleId;

import java.util.ArrayList;
import java.util.List;

public class EmergencyRescueServiceImpl implements IEmergencyRescueService {

    private final EmergencyRescueService emergencyRescueService;

    public EmergencyRescueServiceImpl(
            EmergencyRescueService emergencyRescueService) {
        this.emergencyRescueService = emergencyRescueService;
    }

    @Override
    public Result<Void, AppError> confirmSOSReport(String rescueReportId, String ackToken) {
        Result<Void, AppError> verifyResult = emergencyRescueService.verifyAndConsumeToken(
                ackToken, "CONFIRM_SOS", new VehicleId(rescueReportId));
        if (verifyResult.isErr()) {
            return Result.err(verifyResult.unwrapErr());
        }
        return Result.ok(null);
    }

    @Override
    public Result<IssueRescueTokenResponse, AppError> issueRescueToken(
            String rescueReportId, List<String> operations, int validitySeconds) {
        Result<RescueAuthorizationToken, AppError> result =
                emergencyRescueService.issueRescueAuthorization(
                        new RescueReportId(rescueReportId), operations, validitySeconds);
        if (result.isErr()) {
            return Result.err(result.unwrapErr());
        }
        RescueAuthorizationToken token = result.unwrap();
        return Result.ok(new IssueRescueTokenResponse(
                token.getTokenId(), token.getExpiresAt().toEpochMilli()));
    }

    @Override
    public Result<VerifyTokenResponse, AppError> verifyRescueToken(
            String token, String operation, VehicleId vehicleId) {
        Result<Void, AppError> result = emergencyRescueService.verifyAndConsumeToken(
                token, operation, vehicleId);
        if (result.isErr()) {
            return Result.err(result.unwrapErr());
        }
        return Result.ok(new VerifyTokenResponse(true, operation));
    }

    @Override
    public Result<RescueHistoryResponse, AppError> queryRescueHistory(
            DriverId driverId, int page, int size) {
        Result<List<EmergencyRescueService.RescueRecord>, AppError> result =
                emergencyRescueService.queryRescueHistory(driverId);
        if (result.isErr()) {
            return Result.err(result.unwrapErr());
        }
        List<EmergencyRescueService.RescueRecord> allRecords = result.unwrap();
        long totalCount = allRecords.size();
        int fromIndex = (page - 1) * size;
        if (fromIndex >= allRecords.size()) {
            return Result.ok(new RescueHistoryResponse(new ArrayList<>(), totalCount));
        }
        int toIndex = Math.min(fromIndex + size, allRecords.size());
        List<EmergencyRescueService.RescueRecord> paged =
                allRecords.subList(fromIndex, toIndex);

        List<RescueRecordItem> items = new ArrayList<>();
        for (EmergencyRescueService.RescueRecord r : paged) {
            items.add(new RescueRecordItem(
                    r.recordId(), r.driverId(), r.timestamp(), r.status()));
        }
        return Result.ok(new RescueHistoryResponse(items, totalCount));
    }

    @Override
    public Result<CreateRescueReportResponse, AppError> createRescueReport(
            DriverId driverId, VehicleId vehicleId) {
        Result<EmergencyRescueService.RescueReport, AppError> result =
                emergencyRescueService.createRescueReport(vehicleId, driverId);
        if (result.isErr()) {
            return Result.err(result.unwrapErr());
        }
        EmergencyRescueService.RescueReport report = result.unwrap();
        return Result.ok(new CreateRescueReportResponse(
                report.reportId(), report.status()));
    }
}
