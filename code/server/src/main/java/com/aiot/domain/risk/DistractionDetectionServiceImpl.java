package com.aiot.domain.risk;

import com.aiot.domain.model.SensorReading;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;
import org.springframework.stereotype.Service;

import java.util.List;

public class DistractionDetectionServiceImpl implements DistractionDetectionService {

    private static final double GAZE_DEVIATION_THRESHOLD_CRITICAL = 35.0;
    private static final double GAZE_DEVIATION_THRESHOLD_WARNING = 20.0;
    private static final double OFF_ROAD_DURATION_THRESHOLD = 3.0;

    @Override
    public Result<DistractionResult, AppError> detectDistraction(SensorReading reading) {
        if (!"GAZE_TRACKER".equals(reading.getChannelType())) {
            return Result.err(AppError.validationFailed(
                    "Invalid channel type for distraction detection: " + reading.getChannelType()));
        }

        List<Double> features = reading.getFeatureVector();
        if (features.isEmpty()) {
            return Result.ok(new DistractionResult("NORMAL", 0.0, "No feature data available"));
        }

        double gazeDeviation = features.size() > 0 ? features.get(0) : 0.0;
        double offRoadDuration = features.size() > 1 ? features.get(1) : 0.0;

        if (gazeDeviation > GAZE_DEVIATION_THRESHOLD_CRITICAL
                && offRoadDuration > OFF_ROAD_DURATION_THRESHOLD) {
            return Result.ok(new DistractionResult("L3_CRITICAL", 0.85,
                    "Critical distraction: gazeDev=" + String.format("%.2f", gazeDeviation)
                            + "°, offRoad=" + String.format("%.2f", offRoadDuration) + "s"));
        }
        if (gazeDeviation > GAZE_DEVIATION_THRESHOLD_WARNING) {
            return Result.ok(new DistractionResult("L2_WARNING", 0.65,
                    "Moderate distraction: gazeDev=" + String.format("%.2f", gazeDeviation) + "°"));
        }

        return Result.ok(new DistractionResult("NORMAL", gazeDeviation / GAZE_DEVIATION_THRESHOLD_WARNING,
                "Within normal range"));
    }
}
