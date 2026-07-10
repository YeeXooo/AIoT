package com.aiot.domain.risk;

import com.aiot.domain.event.RiskLevel;
import com.aiot.domain.model.SensorReading;
import com.aiot.domain.shared.TripId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RoadRageDeterminationServiceImplTest {

    private RoadRageDeterminationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RoadRageDeterminationServiceImpl();
    }

    private SensorReading voice(Map<String, Double> values) {
        return new SensorReading(SensorReading.SensorType.MICROPHONE, Instant.now(),
                new TripId("trip-1"), values);
    }

    private SensorReading physio(Map<String, Double> values) {
        return new SensorReading(SensorReading.SensorType.PHYSIOLOGICAL_MONITOR, Instant.now(),
                new TripId("trip-1"), values);
    }

    @Test
    void rejectsNullVoiceReading() {
        var result = service.determineRoadRage(null, physio(Map.of("heartRate", 80.0)));

        assertTrue(result.isErr());
        assertEquals("ValidationFailed", result.unwrapErr().code());
    }

    @Test
    void rejectsWrongVoiceChannel() {
        var result = service.determineRoadRage(
                physio(Map.of("heartRate", 80.0)), physio(Map.of("heartRate", 80.0)));

        assertTrue(result.isErr());
    }

    @Test
    void rejectsNullPhysioReading() {
        var result = service.determineRoadRage(voice(Map.of("voiceIntensity", 90.0)), null);

        assertTrue(result.isErr());
        assertEquals("ValidationFailed", result.unwrapErr().code());
    }

    @Test
    void rejectsWrongPhysioChannel() {
        var result = service.determineRoadRage(
                voice(Map.of("voiceIntensity", 90.0)), voice(Map.of("heartRate", 80.0)));

        assertTrue(result.isErr());
    }

    @Test
    void returnsNoDataWhenBothEmpty() {
        var result = service.determineRoadRage(voice(Map.of()), physio(Map.of()));

        assertTrue(result.isOk());
        assertNull(result.unwrap().riskLevel());
        assertEquals("No feature data available", result.unwrap().summary());
    }

    @Test
    void criticalWhenAllThreeExceed() {
        var result = service.determineRoadRage(
                voice(Map.of("voiceIntensity", 85.0, "emotionIndex", 0.8)),
                physio(Map.of("heartRate", 110.0)));

        assertTrue(result.isOk());
        assertEquals(RiskLevel.L3_CRITICAL, result.unwrap().riskLevel());
        assertEquals(0.9, result.unwrap().confidence());
    }

    @Test
    void warningWhenVoiceAndEmotionExceedButHeartRateNormal() {
        var result = service.determineRoadRage(
                voice(Map.of("voiceIntensity", 70.0, "emotionIndex", 0.7)),
                physio(Map.of("heartRate", 80.0)));

        assertTrue(result.isOk());
        assertEquals(RiskLevel.L2_WARNING, result.unwrap().riskLevel());
        assertEquals(0.7, result.unwrap().confidence());
    }

    @Test
    void normalWhenWithinRange() {
        var result = service.determineRoadRage(
                voice(Map.of("voiceIntensity", 50.0, "emotionIndex", 0.2)),
                physio(Map.of("heartRate", 70.0)));

        assertTrue(result.isOk());
        assertNull(result.unwrap().riskLevel());
        assertEquals("Within normal range", result.unwrap().summary());
    }

    @Test
    void usesDefaultHeartRateWhenPhysioEmptyButVoicePresent() {
        var result = service.determineRoadRage(
                voice(Map.of("voiceIntensity", 85.0, "emotionIndex", 0.8)),
                physio(Map.of()));

        assertTrue(result.isOk());
        assertEquals(RiskLevel.L2_WARNING, result.unwrap().riskLevel());
    }
}
