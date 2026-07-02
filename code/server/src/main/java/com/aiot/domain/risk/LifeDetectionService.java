package com.aiot.domain.risk;

import com.aiot.domain.event.LifeDetectedEvent;
import com.aiot.domain.model.DetectionWindow;
import com.aiot.domain.model.SensorReading;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.VehicleId;

public interface LifeDetectionService {
    Result<DetectionResult, AppError> evaluateLifeDetection(SensorReading radarSignal, DetectionWindow window, VehicleId vehicleId);
    record DetectionResult(DetectionWindow updatedWindow, LifeDetectedEvent detectedEvent) { }
}
