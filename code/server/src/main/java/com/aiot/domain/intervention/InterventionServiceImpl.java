package com.aiot.domain.intervention;

import com.aiot.domain.event.AlertType;
import com.aiot.domain.event.RiskLevel;
import com.aiot.domain.model.InterventionInstruction;
import com.aiot.domain.model.InterventionInstructionType;
import com.aiot.domain.model.OverrideSignal;
import com.aiot.domain.model.OverrideType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service("domainInterventionService")
public class InterventionServiceImpl implements InterventionService {

    private static final int PRIORITY_LOW = 10;
    private static final int PRIORITY_MEDIUM = 20;
    private static final int PRIORITY_HIGH = 30;
    private static final int PRIORITY_URGENT = 40;

    private final Map<AlertType, Map<RiskLevel, List<InterventionInstruction>>> mappingTable;

    public InterventionServiceImpl() {
        this.mappingTable = new EnumMap<>(AlertType.class);
        buildMappingTable();
    }

    private void buildMappingTable() {
        mappingTable.put(AlertType.FATIGUE, buildFatigueMappings());
        mappingTable.put(AlertType.DISTRACTION, buildDistractionMappings());
        mappingTable.put(AlertType.ROAD_RAGE, buildRoadRageMappings());
        mappingTable.put(AlertType.PERFORMANCE_WARNING, buildPerformanceWarningMappings());
    }

    private Map<RiskLevel, List<InterventionInstruction>> buildFatigueMappings() {
        Map<RiskLevel, List<InterventionInstruction>> map = new EnumMap<>(RiskLevel.class);
        map.put(RiskLevel.L1_HINT, List.of(
                InterventionInstruction.of(InterventionInstructionType.AMBIENT_LIGHT_COLOR,
                        "CABIN_LIGHT", Map.<String, Object>of("color", "COOL_WHITE"), PRIORITY_LOW)
        ));
        map.put(RiskLevel.L2_WARNING, List.of(
                InterventionInstruction.of(InterventionInstructionType.AMBIENT_LIGHT_COLOR,
                        "CABIN_LIGHT", Map.<String, Object>of("color", "ORANGE"), PRIORITY_MEDIUM)
        ));
        map.put(RiskLevel.L3_CRITICAL, List.of(
                InterventionInstruction.of(InterventionInstructionType.HAZARD_LIGHTS,
                        "EXTERIOR_LIGHTS", Map.<String, Object>of("mode", "double_flash"), PRIORITY_HIGH),
                InterventionInstruction.of(InterventionInstructionType.AIR_CONDITIONING,
                        "HVAC", Map.<String, Object>of("temperature", "LOW", "fan", "MAX"), PRIORITY_HIGH),
                InterventionInstruction.of(InterventionInstructionType.AUDIO_PLAYBACK,
                        "SPEAKER", Map.<String, Object>of("audio", "alert_tone"), PRIORITY_HIGH),
                InterventionInstruction.of(InterventionInstructionType.NAVIGATE_DECELERATION,
                        "NAVIGATION", Map.<String, Object>of("speed_reduction", "true"), PRIORITY_URGENT)
        ));
        return map;
    }

    private Map<RiskLevel, List<InterventionInstruction>> buildDistractionMappings() {
        Map<RiskLevel, List<InterventionInstruction>> map = new EnumMap<>(RiskLevel.class);
        map.put(RiskLevel.L1_HINT, List.of(
                InterventionInstruction.of(InterventionInstructionType.AMBIENT_LIGHT_COLOR,
                        "CABIN_LIGHT", Map.<String, Object>of("color", "BLUE"), PRIORITY_LOW)
        ));
        map.put(RiskLevel.L2_WARNING, List.of(
                InterventionInstruction.of(InterventionInstructionType.VOICE_BROADCAST,
                        "SPEAKER", Map.<String, Object>of("message", "distraction_warning"), PRIORITY_MEDIUM)
        ));
        map.put(RiskLevel.L3_CRITICAL, List.of(
                InterventionInstruction.of(InterventionInstructionType.VOICE_BROADCAST,
                        "SPEAKER", Map.<String, Object>of("message", "distraction_critical"), PRIORITY_HIGH),
                InterventionInstruction.of(InterventionInstructionType.SEAT_VIBRATION,
                        "DRIVER_SEAT", Map.<String, Object>of("intensity", "high"), PRIORITY_HIGH),
                InterventionInstruction.of(InterventionInstructionType.ALERT,
                        "CLUSTER", Map.<String, Object>of("type", "distraction"), PRIORITY_URGENT)
        ));
        return map;
    }

    private Map<RiskLevel, List<InterventionInstruction>> buildRoadRageMappings() {
        Map<RiskLevel, List<InterventionInstruction>> map = new EnumMap<>(RiskLevel.class);
        map.put(RiskLevel.L1_HINT, List.of(
                InterventionInstruction.of(InterventionInstructionType.AMBIENT_LIGHT_COLOR,
                        "CABIN_LIGHT", Map.<String, Object>of("color", "SOFT_YELLOW"), PRIORITY_LOW)
        ));
        map.put(RiskLevel.L2_WARNING, List.of(
                InterventionInstruction.of(InterventionInstructionType.AUDIO_PLAYBACK,
                        "SPEAKER", Map.<String, Object>of("audio", "calming_music"), PRIORITY_MEDIUM)
        ));
        map.put(RiskLevel.L3_CRITICAL, List.of(
                InterventionInstruction.of(InterventionInstructionType.CAN_DECELERATION_REQUEST,
                        "CAN_BUS", Map.<String, Object>of("deceleration_rate", "gradual"), PRIORITY_HIGH),
                InterventionInstruction.of(InterventionInstructionType.ALERT,
                        "CLUSTER", Map.<String, Object>of("type", "road_rage"), PRIORITY_URGENT),
                InterventionInstruction.of(InterventionInstructionType.NAVIGATE_DECELERATION,
                        "NAVIGATION", Map.<String, Object>of("speed_reduction", "true"), PRIORITY_URGENT)
        ));
        return map;
    }

    private Map<RiskLevel, List<InterventionInstruction>> buildPerformanceWarningMappings() {
        Map<RiskLevel, List<InterventionInstruction>> map = new EnumMap<>(RiskLevel.class);
        map.put(RiskLevel.L1_HINT, List.of(
                InterventionInstruction.of(InterventionInstructionType.AMBIENT_LIGHT_COLOR,
                        "CABIN_LIGHT", Map.<String, Object>of("color", "WARM_WHITE"), PRIORITY_LOW)
        ));
        map.put(RiskLevel.L2_WARNING, List.of(
                InterventionInstruction.of(InterventionInstructionType.ALERT,
                        "CLUSTER", Map.<String, Object>of("type", "performance_warning"), PRIORITY_MEDIUM)
        ));
        map.put(RiskLevel.L3_CRITICAL, List.of(
                InterventionInstruction.of(InterventionInstructionType.VOICE_BROADCAST,
                        "SPEAKER", Map.<String, Object>of("message", "performance_critical"), PRIORITY_HIGH),
                InterventionInstruction.of(InterventionInstructionType.ALERT,
                        "CLUSTER", Map.<String, Object>of("type", "performance_critical"), PRIORITY_URGENT)
        ));
        return map;
    }

    @Override
    public List<InterventionInstruction> generateIntervention(AlertType alertType, RiskLevel riskLevel) {
        Map<RiskLevel, List<InterventionInstruction>> riskMap = mappingTable.get(alertType);
        if (riskMap == null) {
            return Collections.emptyList();
        }
        return riskMap.getOrDefault(riskLevel, Collections.emptyList());
    }

    @Override
    public InterventionResult handleOverride(OverrideSignal signal) {
        if (signal.getType() == OverrideType.BRAKING || signal.getType() == OverrideType.TURNING
                || signal.getType() == OverrideType.ACCELERATING) {
            return new InterventionResult(OverrideResult.ABORTED, signal.getTimestamp().toEpochMilli());
        }
        if (signal.getType() == OverrideType.RESUMING) {
            return new InterventionResult(OverrideResult.RESUMED, signal.getTimestamp().toEpochMilli());
        }
        return new InterventionResult(OverrideResult.CONTINUING, signal.getTimestamp().toEpochMilli());
    }
}
