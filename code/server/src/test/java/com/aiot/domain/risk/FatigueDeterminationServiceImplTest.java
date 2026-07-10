package com.aiot.domain.risk;

import com.aiot.domain.event.RiskLevel;
import com.aiot.domain.model.SensorReading;
import com.aiot.domain.shared.TripId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FatigueDeterminationServiceImplTest {

    private FatigueDeterminationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FatigueDeterminationServiceImpl();
    }

    private SensorReading dms(Map<String, Double> values) {
        return new SensorReading(SensorReading.SensorType.DMS_CAMERA, Instant.now(),
                new TripId("trip-1"), values);
    }

    @Test
    void rejectsNonDmsChannel() {
        SensorReading reading = new SensorReading(SensorReading.SensorType.MICROPHONE,
                Instant.now(), new TripId("trip-1"), Map.of("PERCLOS", 0.9));

        var result = service.determineFatigue(reading);

        assertTrue(result.isErr());
        assertEquals("ValidationFailed", result.unwrapErr().code());
    }

    @Test
    void returnsNoDataWhenValuesEmpty() {
        var result = service.determineFatigue(dms(Map.of()));

        assertTrue(result.isOk());
        assertNull(result.unwrap().riskLevel());
        assertEquals("No feature data available", result.unwrap().summary());
    }

    @Test
    void criticalWhenPerclosAboveCriticalThreshold() {
        var result = service.determineFatigue(dms(Map.of("PERCLOS", 0.8, "blinkRate", 15.0)));

        assertTrue(result.isOk());
        assertEquals(RiskLevel.L3_CRITICAL, result.unwrap().riskLevel());
        assertEquals(0.9, result.unwrap().confidence());
    }

    @Test
    void criticalWhenBlinkRateBelowCriticalThreshold() {
        var result = service.determineFatigue(dms(Map.of("PERCLOS", 0.1, "blinkRate", 3.0)));

        assertTrue(result.isOk());
        assertEquals(RiskLevel.L3_CRITICAL, result.unwrap().riskLevel());
    }

    @Test
    void warningWhenPerclosAboveWarningThreshold() {
        var result = service.determineFatigue(dms(Map.of("PERCLOS", 0.6, "blinkRate", 15.0)));

        assertTrue(result.isOk());
        assertEquals(RiskLevel.L2_WARNING, result.unwrap().riskLevel());
        assertEquals(0.7, result.unwrap().confidence());
    }

    @Test
    void warningWhenBlinkRateBelowWarningThreshold() {
        var result = service.determineFatigue(dms(Map.of("PERCLOS", 0.1, "blinkRate", 8.0)));

        assertTrue(result.isOk());
        assertEquals(RiskLevel.L2_WARNING, result.unwrap().riskLevel());
    }

    @Test
    void normalWhenWithinRange() {
        var result = service.determineFatigue(dms(Map.of("PERCLOS", 0.2, "blinkRate", 15.0)));

        assertTrue(result.isOk());
        assertNull(result.unwrap().riskLevel());
        assertTrue(result.unwrap().summary().startsWith("Within normal range"));
    }
}
