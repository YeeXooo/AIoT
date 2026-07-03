package com.aiot.domain.intervention;

import com.aiot.domain.event.AlertType;
import com.aiot.domain.event.RiskLevel;
import com.aiot.domain.model.InterventionInstruction;
import com.aiot.domain.model.InterventionInstructionType;
import com.aiot.domain.model.OverrideSignal;
import com.aiot.domain.model.OverrideType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InterventionServiceImplTest {

    private InterventionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InterventionServiceImpl();
    }

    @Test
    void generateInterventionFatigueL1HintReturnsInstructions() {
        List<InterventionInstruction> instructions = service.generateIntervention(AlertType.FATIGUE, RiskLevel.L1_HINT);

        assertNotNull(instructions);
        assertFalse(instructions.isEmpty());
        assertEquals(1, instructions.size());
        assertEquals(InterventionInstructionType.AMBIENT_LIGHT_COLOR, instructions.get(0).getType());
    }

    @Test
    void generateInterventionFatigueL2WarningReturnsInstructions() {
        List<InterventionInstruction> instructions = service.generateIntervention(AlertType.FATIGUE, RiskLevel.L2_WARNING);

        assertNotNull(instructions);
        assertFalse(instructions.isEmpty());
        assertEquals(1, instructions.size());
    }

    @Test
    void generateInterventionFatigueL3CriticalReturnsInstructions() {
        List<InterventionInstruction> instructions = service.generateIntervention(AlertType.FATIGUE, RiskLevel.L3_CRITICAL);

        assertNotNull(instructions);
        assertFalse(instructions.isEmpty());
        assertTrue(instructions.size() >= 3);
        boolean hasUrgent = instructions.stream().anyMatch(i -> i.getPriority() >= 40);
        assertTrue(hasUrgent);
    }

    @Test
    void generateInterventionDistractionL1HintReturnsInstructions() {
        List<InterventionInstruction> instructions = service.generateIntervention(AlertType.DISTRACTION, RiskLevel.L1_HINT);

        assertNotNull(instructions);
        assertFalse(instructions.isEmpty());
        assertEquals(InterventionInstructionType.AMBIENT_LIGHT_COLOR, instructions.get(0).getType());
    }

    @Test
    void generateInterventionDistractionL2WarningReturnsInstructions() {
        List<InterventionInstruction> instructions = service.generateIntervention(AlertType.DISTRACTION, RiskLevel.L2_WARNING);

        assertNotNull(instructions);
        assertFalse(instructions.isEmpty());
        assertEquals(InterventionInstructionType.VOICE_BROADCAST, instructions.get(0).getType());
    }

    @Test
    void generateInterventionDistractionL3CriticalReturnsInstructions() {
        List<InterventionInstruction> instructions = service.generateIntervention(AlertType.DISTRACTION, RiskLevel.L3_CRITICAL);

        assertNotNull(instructions);
        assertFalse(instructions.isEmpty());
        assertTrue(instructions.size() >= 2);
    }

    @Test
    void generateInterventionRoadRageL1HintReturnsInstructions() {
        List<InterventionInstruction> instructions = service.generateIntervention(AlertType.ROAD_RAGE, RiskLevel.L1_HINT);

        assertNotNull(instructions);
        assertFalse(instructions.isEmpty());
    }

    @Test
    void generateInterventionRoadRageL2WarningReturnsInstructions() {
        List<InterventionInstruction> instructions = service.generateIntervention(AlertType.ROAD_RAGE, RiskLevel.L2_WARNING);

        assertNotNull(instructions);
        assertFalse(instructions.isEmpty());
        assertEquals(InterventionInstructionType.AUDIO_PLAYBACK, instructions.get(0).getType());
    }

    @Test
    void generateInterventionRoadRageL3CriticalReturnsInstructions() {
        List<InterventionInstruction> instructions = service.generateIntervention(AlertType.ROAD_RAGE, RiskLevel.L3_CRITICAL);

        assertNotNull(instructions);
        assertFalse(instructions.isEmpty());
        assertTrue(instructions.size() >= 2);
    }

    @Test
    void generateInterventionPerformanceWarningL1HintReturnsInstructions() {
        List<InterventionInstruction> instructions = service.generateIntervention(AlertType.PERFORMANCE_WARNING, RiskLevel.L1_HINT);

        assertNotNull(instructions);
        assertFalse(instructions.isEmpty());
    }

    @Test
    void generateInterventionPerformanceWarningL2WarningReturnsInstructions() {
        List<InterventionInstruction> instructions = service.generateIntervention(AlertType.PERFORMANCE_WARNING, RiskLevel.L2_WARNING);

        assertNotNull(instructions);
        assertFalse(instructions.isEmpty());
        assertEquals(InterventionInstructionType.ALERT, instructions.get(0).getType());
    }

    @Test
    void generateInterventionPerformanceWarningL3CriticalReturnsInstructions() {
        List<InterventionInstruction> instructions = service.generateIntervention(AlertType.PERFORMANCE_WARNING, RiskLevel.L3_CRITICAL);

        assertNotNull(instructions);
        assertFalse(instructions.isEmpty());
        assertTrue(instructions.size() >= 2);
    }

    @Test
    void generateInterventionReturnsEmptyListForUnknownAlertType() {
        List<InterventionInstruction> instructions = service.generateIntervention(AlertType.LIFE_DETECTION, RiskLevel.L1_HINT);

        assertNotNull(instructions);
        assertTrue(instructions.isEmpty());
    }

    @Test
    void generateInterventionReturnsEmptyListForUnknownRiskLevelInKnownType() {
        List<InterventionInstruction> instructions = service.generateIntervention(AlertType.COLLISION_DISABILITY, RiskLevel.L1_HINT);

        assertNotNull(instructions);
        assertTrue(instructions.isEmpty());
    }

    @Test
    void handleOverrideBrakingReturnsAborted() {
        OverrideSignal signal = OverrideSignal.of(OverrideType.BRAKING, Instant.now());

        InterventionService.InterventionResult result = service.handleOverride(signal);

        assertEquals(InterventionService.OverrideResult.ABORTED, result.status());
    }

    @Test
    void handleOverrideTurningReturnsAborted() {
        OverrideSignal signal = OverrideSignal.of(OverrideType.TURNING, Instant.now());

        InterventionService.InterventionResult result = service.handleOverride(signal);

        assertEquals(InterventionService.OverrideResult.ABORTED, result.status());
    }

    @Test
    void handleOverrideAcceleratingReturnsAborted() {
        OverrideSignal signal = OverrideSignal.of(OverrideType.ACCELERATING, Instant.now());

        InterventionService.InterventionResult result = service.handleOverride(signal);

        assertEquals(InterventionService.OverrideResult.ABORTED, result.status());
    }

    @Test
    void handleOverrideResumingReturnsResumed() {
        OverrideSignal signal = OverrideSignal.of(OverrideType.RESUMING, Instant.now());

        InterventionService.InterventionResult result = service.handleOverride(signal);

        assertEquals(InterventionService.OverrideResult.RESUMED, result.status());
    }

    @Test
    void handleOverrideTimestampIsPreserved() {
        Instant now = Instant.now();
        OverrideSignal signal = OverrideSignal.of(OverrideType.BRAKING, now);

        InterventionService.InterventionResult result = service.handleOverride(signal);

        assertEquals(now.toEpochMilli(), result.timestamp());
    }
}
