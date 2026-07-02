package com.aiot.domain.risk;

import com.aiot.domain.model.SensorReading;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;

public interface FatigueDeterminationService {

    Result<FatigueResult, AppError> determineFatigue(SensorReading reading);

    record FatigueResult(String riskLevel, double confidence, String summary) {}
}
