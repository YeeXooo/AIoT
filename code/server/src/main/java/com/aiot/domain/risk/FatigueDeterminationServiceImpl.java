package com.aiot.domain.risk;

import com.aiot.domain.model.SensorReading;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;
import org.springframework.stereotype.Service;

import java.util.List;

public class FatigueDeterminationServiceImpl implements FatigueDeterminationService {

    private static final double PERCLOS_THRESHOLD_CRITICAL = 0.7;
    private static final double PERCLOS_THRESHOLD_WARNING = 0.5;
    private static final double BLINK_RATE_THRESHOLD_WARNING = 10.0;
    private static final double BLINK_RATE_THRESHOLD_CRITICAL = 5.0;

    @Override
    public Result<FatigueResult, AppError> determineFatigue(SensorReading reading) {
        if (!"FACE_CAMERA".equals(reading.getChannelType())) {
            return Result.err(AppError.validationFailed(
                    "Invalid channel type for fatigue determination: " + reading.getChannelType()));
        }

        List<Double> features = reading.getFeatureVector();
        if (features.isEmpty()) {
            return Result.ok(new FatigueResult("NORMAL", 0.0, "No feature data available"));
        }

        double perclos = features.size() > 0 ? features.get(0) : 0.0;
        double blinkRate = features.size() > 1 ? features.get(1) : 15.0;

        if (perclos > PERCLOS_THRESHOLD_CRITICAL || blinkRate < BLINK_RATE_THRESHOLD_CRITICAL) {
            return Result.ok(new FatigueResult("L3_CRITICAL", 0.9,
                    "Critical fatigue: PERCLOS=" + String.format("%.2f", perclos)
                            + ", blinkRate=" + String.format("%.2f", blinkRate)));
        }
        if (perclos > PERCLOS_THRESHOLD_WARNING || blinkRate < BLINK_RATE_THRESHOLD_WARNING) {
            return Result.ok(new FatigueResult("L2_WARNING", 0.7,
                    "Moderate fatigue: PERCLOS=" + String.format("%.2f", perclos)
                            + ", blinkRate=" + String.format("%.2f", blinkRate)));
        }

        return Result.ok(new FatigueResult("NORMAL", perclos,
                "Within normal range: PERCLOS=" + String.format("%.2f", perclos)));
    }
}
