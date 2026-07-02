package com.aiot.domain.risk;

import com.aiot.domain.event.RiskLevel;
import com.aiot.domain.model.SensorReading;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;

public interface DistractionDetectionService {

    Result<DistractionResult, AppError> detectDistraction(SensorReading reading);

    record DistractionResult(RiskLevel riskLevel, double confidence, String summary) { }
}
