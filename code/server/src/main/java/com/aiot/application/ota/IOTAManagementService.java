package com.aiot.application.ota;

import com.aiot.domain.model.OTAVersion;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.VehicleId;

import java.util.List;

public interface IOTAManagementService {
    Result<CreateTaskResponse, AppError> createUpgradeTask(VehicleId vehicleId, OTAVersion targetVersion, String idempotencyKey);
    Result<UpgradeProgressResponse, AppError> queryUpgradeProgress(VehicleId vehicleId);
    Result<RollbackResponse, AppError> triggerRollback(VehicleId vehicleId);
    Result<UpgradeHistoryResponse, AppError> queryUpgradeHistory(VehicleId vehicleId, int page, int size);
    Result<CancelTaskResponse, AppError> cancelUpgradeTask(VehicleId vehicleId);

    record CreateTaskResponse(String taskId, String status) {}
    record UpgradeProgressResponse(String stage, long transferredBytes, long totalBytes, String currentVersion, String targetVersion) {}
    record RollbackResponse(String taskId, String previousStage, String rolledBackVersion) {}
    record UpgradeHistoryResponse(List<UpgradeHistoryItem> items, long totalCount) {}
    record UpgradeHistoryItem(String taskId, String vehicleId, String targetVersion, String status, long createdAt) {}
    record CancelTaskResponse(String taskId, String status) {}
}
