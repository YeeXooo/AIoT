package com.aiot.domain.risk;

import com.aiot.domain.event.RiskLevel;
import com.aiot.domain.model.SensorReading;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;

import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RoadRageDeterminationServiceImpl implements RoadRageDeterminationService {

    private static final double VOICE_INTENSITY_THRESHOLD_CRITICAL = 80.0;
    private static final double VOICE_INTENSITY_THRESHOLD_WARNING = 65.0;
    private static final double EMOTION_INDEX_THRESHOLD = 0.6;
    private static final double HEART_RATE_THRESHOLD = 100.0;

    @Override
    public Result<RoadRageResult, AppError> determineRoadRage(SensorReading voiceReading, SensorReading physioReading) {
        if (voiceReading == null || voiceReading.sensorType() != SensorReading.SensorType.MICROPHONE) {
            return Result.err(AppError.validationFailed(
                    "Invalid voice channel type for road rage determination: "
                            + (voiceReading != null ? voiceReading.sensorType().toString() : "null")));
        }
        if (physioReading == null || physioReading.sensorType() != SensorReading.SensorType.PHYSIOLOGICAL_MONITOR) {
            return Result.err(AppError.validationFailed(
                    "Invalid physio channel type for road rage determination: "
                            + (physioReading != null ? physioReading.sensorType().toString() : "null")));
        }

        Map<String, Double> voiceValues = voiceReading.values();
        Map<String, Double> physioValues = physioReading.values();
        if (voiceValues.isEmpty() && physioValues.isEmpty()) {
            return Result.ok(new RoadRageResult(null, 0.0, "No feature data available"));
        }

        double voiceIntensity = voiceReading.get("voiceIntensity");
        double emotionIndex = voiceReading.get("emotionIndex");
        double heartRate = !physioValues.isEmpty() ? physioReading.get("heartRate") : 70.0;

        if (voiceIntensity > VOICE_INTENSITY_THRESHOLD_CRITICAL
                && emotionIndex > EMOTION_INDEX_THRESHOLD
                && heartRate > HEART_RATE_THRESHOLD) {
            return Result.ok(new RoadRageResult(RiskLevel.L3_CRITICAL, 0.9,
                    "Critical road rage: intensity=" + String.format("%.2f", voiceIntensity)
                            + "dB, emotion=" + String.format("%.2f", emotionIndex)
                            + ", HR=" + String.format("%.0f", heartRate)));
        }
        if (voiceIntensity > VOICE_INTENSITY_THRESHOLD_WARNING
                && emotionIndex > EMOTION_INDEX_THRESHOLD) {
            return Result.ok(new RoadRageResult(RiskLevel.L2_WARNING, 0.7,
                    "Moderate road rage: intensity=" + String.format("%.2f", voiceIntensity)
                            + "dB, emotion=" + String.format("%.2f", emotionIndex)));
        }

        return Result.ok(new RoadRageResult(null, voiceIntensity / VOICE_INTENSITY_THRESHOLD_WARNING,
                "Within normal range"));
    }
}
