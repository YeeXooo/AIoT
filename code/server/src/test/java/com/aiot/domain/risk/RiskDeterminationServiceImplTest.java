package com.aiot.domain.risk;

import com.aiot.domain.event.AlertType;
import com.aiot.domain.event.DomainEventPublisher;
import com.aiot.domain.event.RiskLevel;
import com.aiot.domain.model.SensorReading;
import com.aiot.domain.shared.TripId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiskDeterminationServiceImplTest {

    @Mock
    private FatigueDeterminationService fatigueService;
    @Mock
    private DistractionDetectionService distractionService;
    @Mock
    private RoadRageDeterminationService roadRageService;
    @Mock
    private DomainEventPublisher eventPublisher;

    private RiskDeterminationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RiskDeterminationServiceImpl(
                fatigueService, distractionService, roadRageService, eventPublisher);
    }

    private SensorReading reading(SensorReading.SensorType type) {
        return new SensorReading(type, Instant.now(), new TripId("trip-1"), Map.of("v", 1.0));
    }

    @Test
    void newFatigueRiskProducesDeterminedEvent() {
        when(fatigueService.determineFatigue(any())).thenReturn(com.aiot.domain.shared.Result.ok(
                new com.aiot.domain.risk.FatigueDeterminationService.FatigueResult(
                        RiskLevel.L3_CRITICAL, 0.9, "疲劳")));
        when(distractionService.detectDistraction(any())).thenReturn(com.aiot.domain.shared.Result.ok(
                new com.aiot.domain.risk.DistractionDetectionService.DistractionResult(null, 0.0, "正常")));

        var result = service.executeStreamFusion(
                List.of(reading(SensorReading.SensorType.DMS_CAMERA)),
                ActiveRiskSet.empty("trip-1"));

        assertTrue(result.isOk());
        assertEquals(1, result.unwrap().determinedEvents().size());
        assertTrue(result.unwrap().updatedRiskSet().isActive(AlertType.FATIGUE));
        verify(eventPublisher, atLeastOnce()).publish(any());
    }

    @Test
    void resolvesFatigueWhenNoLongerDetected() {
        when(fatigueService.determineFatigue(any())).thenReturn(com.aiot.domain.shared.Result.ok(
                new com.aiot.domain.risk.FatigueDeterminationService.FatigueResult(null, 0.0, "正常")));
        when(distractionService.detectDistraction(any())).thenReturn(com.aiot.domain.shared.Result.ok(
                new com.aiot.domain.risk.DistractionDetectionService.DistractionResult(null, 0.0, "正常")));

        ActiveRiskSet current = ActiveRiskSet.empty("trip-1")
                .add(AlertType.FATIGUE, RiskLevel.L2_WARNING, Instant.now(), "旧疲劳");

        var result = service.executeStreamFusion(
                List.of(reading(SensorReading.SensorType.DMS_CAMERA)), current);

        assertTrue(result.isOk());
        assertFalse(result.unwrap().updatedRiskSet().isActive(AlertType.FATIGUE));
        assertFalse(result.unwrap().resolvedEvents().isEmpty());
    }

    @Test
    void distractionRiskDetected() {
        when(fatigueService.determineFatigue(any())).thenReturn(com.aiot.domain.shared.Result.ok(
                new com.aiot.domain.risk.FatigueDeterminationService.FatigueResult(null, 0.0, "正常")));
        when(distractionService.detectDistraction(any())).thenReturn(com.aiot.domain.shared.Result.ok(
                new com.aiot.domain.risk.DistractionDetectionService.DistractionResult(
                        RiskLevel.L2_WARNING, 0.65, "分心")));

        var result = service.executeStreamFusion(
                List.of(reading(SensorReading.SensorType.DMS_CAMERA)),
                ActiveRiskSet.empty("trip-1"));

        assertTrue(result.isOk());
        assertTrue(result.unwrap().updatedRiskSet().isActive(AlertType.DISTRACTION));
    }

    @Test
    void roadRageRequiresBothVoiceAndPhysio() {
        when(roadRageService.determineRoadRage(any(), any())).thenReturn(com.aiot.domain.shared.Result.ok(
                new com.aiot.domain.risk.RoadRageDeterminationService.RoadRageResult(
                        RiskLevel.L3_CRITICAL, 0.9, "路怒")));

        var result = service.executeStreamFusion(
                List.of(reading(SensorReading.SensorType.MICROPHONE),
                        reading(SensorReading.SensorType.PHYSIOLOGICAL_MONITOR)),
                ActiveRiskSet.empty("trip-1"));

        assertTrue(result.isOk());
        assertTrue(result.unwrap().updatedRiskSet().isActive(AlertType.ROAD_RAGE));
    }

    @Test
    void roadRageSkippedWhenOnlyVoicePresent() {
        var result = service.executeStreamFusion(
                List.of(reading(SensorReading.SensorType.MICROPHONE)),
                ActiveRiskSet.empty("trip-1"));

        assertTrue(result.isOk());
        assertFalse(result.unwrap().updatedRiskSet().isActive(AlertType.ROAD_RAGE));
        verifyNoInteractions(roadRageService);
    }

    @Test
    void unknownActiveRiskTypeGetsResolved() {
        ActiveRiskSet current = ActiveRiskSet.empty("trip-1")
                .add(AlertType.LIFE_DETECTION, RiskLevel.L2_WARNING, Instant.now(), "活体");

        var result = service.executeStreamFusion(List.of(), current);

        assertTrue(result.isOk());
        assertFalse(result.unwrap().updatedRiskSet().isActive(AlertType.LIFE_DETECTION));
        assertEquals(1, result.unwrap().resolvedEvents().size());
    }

    @Test
    void emptyReadingsWithEmptyRiskSetProducesNothing() {
        var result = service.executeStreamFusion(List.of(), ActiveRiskSet.empty("trip-1"));

        assertTrue(result.isOk());
        assertTrue(result.unwrap().determinedEvents().isEmpty());
        assertTrue(result.unwrap().resolvedEvents().isEmpty());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void alreadyActiveFatigueNotDuplicated() {
        when(fatigueService.determineFatigue(any())).thenReturn(com.aiot.domain.shared.Result.ok(
                new com.aiot.domain.risk.FatigueDeterminationService.FatigueResult(
                        RiskLevel.L3_CRITICAL, 0.9, "疲劳")));
        when(distractionService.detectDistraction(any())).thenReturn(com.aiot.domain.shared.Result.ok(
                new com.aiot.domain.risk.DistractionDetectionService.DistractionResult(null, 0.0, "正常")));

        ActiveRiskSet current = ActiveRiskSet.empty("trip-1")
                .add(AlertType.FATIGUE, RiskLevel.L3_CRITICAL, Instant.now(), "疲劳");

        var result = service.executeStreamFusion(
                List.of(reading(SensorReading.SensorType.DMS_CAMERA)), current);

        assertTrue(result.isOk());
        assertTrue(result.unwrap().determinedEvents().isEmpty());
    }
}
