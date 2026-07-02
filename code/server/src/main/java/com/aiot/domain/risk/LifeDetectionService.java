package com.aiot.domain.risk;

import com.aiot.domain.event.LifeDetectedEvent;
import com.aiot.domain.model.DetectionWindow;
import com.aiot.domain.model.SensorReading;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;

public interface LifeDetectionService {
    Result<DetectionResult, AppError> evaluateLifeDetection(SensorReading radarSignal, DetectionWindow window);
    record DetectionResult(DetectionWindow updatedWindow, LifeDetectedEvent detectedEvent) {}
}
