package com.aiot.domain.risk;

import com.aiot.domain.event.RiskLevel;
import com.aiot.domain.model.SensorReading;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;

public class FatigueDeterminationServiceImpl implements FatigueDeterminationService {

    private static final double PERCLOS_THRESHOLD_CRITICAL = 0.7;
    private static final double PERCLOS_THRESHOLD_WARNING = 0.5;
    private static final double BLINK_RATE_THRESHOLD_WARNING = 10.0;
    private static final double BLINK_RATE_THRESHOLD_CRITICAL = 5.0;

    @Override
    public Result<FatigueResult, AppError> determineFatigue(SensorReading reading) {
        if (reading.sensorType() != SensorReading.SensorType.DMS_CAMERA) {
            return Result.err(AppError.validationFailed(
                    "Invalid channel type for fatigue determination: " + reading.sensorType()));
        }

        if (reading.values().isEmpty()) {
            return Result.ok(new FatigueResult(null, 0.0, "No feature data available"));
        }

        double perclos = reading.perclos();
        double blinkRate = reading.get("blinkRate");

        if (perclos > PERCLOS_THRESHOLD_CRITICAL || blinkRate < BLINK_RATE_THRESHOLD_CRITICAL) {
            return Result.ok(new FatigueResult(RiskLevel.L3_CRITICAL, 0.9,
                    "Critical fatigue: PERCLOS=" + String.format("%.2f", perclos)
                            + ", blinkRate=" + String.format("%.2f", blinkRate)));
        }
        if (perclos > PERCLOS_THRESHOLD_WARNING || blinkRate < BLINK_RATE_THRESHOLD_WARNING) {
            return Result.ok(new FatigueResult(RiskLevel.L2_WARNING, 0.7,
                    "Moderate fatigue: PERCLOS=" + String.format("%.2f", perclos)
                            + ", blinkRate=" + String.format("%.2f", blinkRate)));
        }

        return Result.ok(new FatigueResult(null, perclos,
                "Within normal range: PERCLOS=" + String.format("%.2f", perclos)));
    }
}
