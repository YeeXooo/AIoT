package com.aiot.domain.ota;

import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.VehicleId;

import java.util.List;
import java.util.Map;

public interface SensorSelfCheckService {
    Result<SelfCheckResult, AppError> runSelfCheck(VehicleId vehicleId);
    void onOcclusionDetected(String vehicleId, String sensorId, long timestamp);
    void onOcclusionRemoved(String vehicleId, String sensorId, long timestamp);
    record SelfCheckResult(Map<String, String> sensorStatuses, List<String> occludedSensors) {}
}
