package com.aiot.domain.risk;

import com.aiot.domain.model.SensorReading;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;

public interface RoadRageDeterminationService {

    Result<RoadRageResult, AppError> determineRoadRage(SensorReading reading);

    record RoadRageResult(String riskLevel, double confidence, String summary) {}
}
