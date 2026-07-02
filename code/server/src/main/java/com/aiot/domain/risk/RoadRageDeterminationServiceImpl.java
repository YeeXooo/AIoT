package com.aiot.domain.risk;

import com.aiot.domain.model.SensorReading;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;
import org.springframework.stereotype.Service;

import java.util.List;

public class RoadRageDeterminationServiceImpl implements RoadRageDeterminationService {

    private static final double VOICE_INTENSITY_THRESHOLD_CRITICAL = 80.0;
    private static final double VOICE_INTENSITY_THRESHOLD_WARNING = 65.0;
    private static final double EMOTION_INDEX_THRESHOLD = 0.6;
    private static final double HEART_RATE_THRESHOLD = 100.0;

    @Override
    public Result<RoadRageResult, AppError> determineRoadRage(SensorReading reading) {
        if (!"AUDIO_SENSOR".equals(reading.getChannelType())) {
            return Result.err(AppError.validationFailed(
                    "Invalid channel type for road rage determination: " + reading.getChannelType()));
        }

        List<Double> features = reading.getFeatureVector();
        if (features.isEmpty()) {
            return Result.ok(new RoadRageResult("NORMAL", 0.0, "No feature data available"));
        }

        double voiceIntensity = features.size() > 0 ? features.get(0) : 0.0;
        double emotionIndex = features.size() > 1 ? features.get(1) : 0.0;
        double heartRate = features.size() > 2 ? features.get(2) : 70.0;

        if (voiceIntensity > VOICE_INTENSITY_THRESHOLD_CRITICAL
                && emotionIndex > EMOTION_INDEX_THRESHOLD
                && heartRate > HEART_RATE_THRESHOLD) {
            return Result.ok(new RoadRageResult("L3_CRITICAL", 0.9,
                    "Critical road rage: intensity=" + String.format("%.2f", voiceIntensity)
                            + "dB, emotion=" + String.format("%.2f", emotionIndex)
                            + ", HR=" + String.format("%.0f", heartRate)));
        }
        if (voiceIntensity > VOICE_INTENSITY_THRESHOLD_WARNING
                && emotionIndex > EMOTION_INDEX_THRESHOLD) {
            return Result.ok(new RoadRageResult("L2_WARNING", 0.7,
                    "Moderate road rage: intensity=" + String.format("%.2f", voiceIntensity)
                            + "dB, emotion=" + String.format("%.2f", emotionIndex)));
        }

        return Result.ok(new RoadRageResult("NORMAL", voiceIntensity / VOICE_INTENSITY_THRESHOLD_WARNING,
                "Within normal range"));
    }
}
