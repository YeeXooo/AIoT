package com.aiot.domain.ota;

import com.aiot.domain.model.OTAVersion;
import com.aiot.domain.model.UpgradeStage;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.VehicleId;

public interface OTAUpdateService {
    Result<Void, AppError> initiateUpgrade(VehicleId vehicleId, OTAVersion targetVersion);
    Result<UpgradeStage, AppError> handleTransferProgress(VehicleId vehicleId, long transferredBytes, long totalBytes, String status);
    Result<UpgradeStage, AppError> handleVerificationResult(VehicleId vehicleId, boolean checksumValid);
    Result<UpgradeStage, AppError> handleFirmwareFlashResult(VehicleId vehicleId, boolean flashSuccess);
}
