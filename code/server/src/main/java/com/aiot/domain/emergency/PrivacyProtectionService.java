package com.aiot.domain.emergency;

import com.aiot.domain.model.SensorReading;
import com.aiot.domain.shared.AlertId;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.Result;

public interface PrivacyProtectionService {
    Result<Void, AppError> validateDataDesensitization(SensorReading reading);
    Result<String, AppError> startVoiceRecording(AlertId alertId, DriverId driverId);
    Result<Void, AppError> sealVoiceRecord(String recordId);
    Result<Integer, AppError> purgeExpiredRecords();
}
