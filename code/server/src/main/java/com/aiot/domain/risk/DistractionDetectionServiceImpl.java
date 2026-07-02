package com.aiot.domain.risk;

import com.aiot.domain.event.RiskLevel;
import com.aiot.domain.model.SensorReading;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;
import org.springframework.stereotype.Service;

@Service
public class DistractionDetectionServiceImpl implements DistractionDetectionService {

    private static final double GAZE_DEVIATION_THRESHOLD_CRITICAL = 35.0;
    private static final double GAZE_DEVIATION_THRESHOLD_WARNING = 20.0;
    private static final double OFF_ROAD_DURATION_THRESHOLD = 3.0;

    @Override
    public Result<DistractionResult, AppError> detectDistraction(SensorReading reading) {
        if (reading.sensorType() != SensorReading.SensorType.DMS_CAMERA) {
            return Result.err(AppError.validationFailed(
                    "Invalid channel type for distraction detection: " + reading.sensorType()));
        }

        if (reading.values().isEmpty()) {
            return Result.ok(new DistractionResult(null, 0.0, "No feature data available"));
        }

        double gazeDeviation = reading.get("gazeDeviation");
        double offRoadDuration = reading.get("offRoadDuration");

        if (gazeDeviation > GAZE_DEVIATION_THRESHOLD_CRITICAL
                && offRoadDuration > OFF_ROAD_DURATION_THRESHOLD) {
            return Result.ok(new DistractionResult(RiskLevel.L3_CRITICAL, 0.85,
                    "Critical distraction: gazeDev=" + String.format("%.2f", gazeDeviation)
                            + "°, offRoad=" + String.format("%.2f", offRoadDuration) + "s"));
        }
        if (gazeDeviation > GAZE_DEVIATION_THRESHOLD_WARNING) {
            return Result.ok(new DistractionResult(RiskLevel.L2_WARNING, 0.65,
                    "Moderate distraction: gazeDev=" + String.format("%.2f", gazeDeviation) + "°"));
        }

        return Result.ok(new DistractionResult(null, gazeDeviation / GAZE_DEVIATION_THRESHOLD_WARNING,
                "Within normal range"));
    }
}
