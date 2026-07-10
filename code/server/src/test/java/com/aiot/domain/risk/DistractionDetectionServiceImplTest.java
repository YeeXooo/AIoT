package com.aiot.domain.risk;

import com.aiot.domain.event.RiskLevel;
import com.aiot.domain.model.SensorReading;
import com.aiot.domain.shared.TripId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DistractionDetectionServiceImplTest {

    private DistractionDetectionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DistractionDetectionServiceImpl();
    }

    private SensorReading dms(Map<String, Double> values) {
        return new SensorReading(SensorReading.SensorType.DMS_CAMERA, Instant.now(),
                new TripId("trip-1"), values);
    }

    @Test
    void rejectsNonDmsChannel() {
        SensorReading reading = new SensorReading(SensorReading.SensorType.ACCELEROMETER,
                Instant.now(), new TripId("trip-1"), Map.of("gazeDeviation", 40.0));

        var result = service.detectDistraction(reading);

        assertTrue(result.isErr());
        assertEquals("ValidationFailed", result.unwrapErr().code());
    }

    @Test
    void returnsNoDataWhenValuesEmpty() {
        var result = service.detectDistraction(dms(Map.of()));

        assertTrue(result.isOk());
        assertNull(result.unwrap().riskLevel());
    }

    @Test
    void criticalWhenGazeAndOffRoadBothExceed() {
        var result = service.detectDistraction(
                dms(Map.of("gazeDeviation", 40.0, "offRoadDuration", 5.0)));

        assertTrue(result.isOk());
        assertEquals(RiskLevel.L3_CRITICAL, result.unwrap().riskLevel());
        assertEquals(0.85, result.unwrap().confidence());
    }

    @Test
    void warningWhenOnlyGazeExceedsWarning() {
        var result = service.detectDistraction(
                dms(Map.of("gazeDeviation", 25.0, "offRoadDuration", 1.0)));

        assertTrue(result.isOk());
        assertEquals(RiskLevel.L2_WARNING, result.unwrap().riskLevel());
        assertEquals(0.65, result.unwrap().confidence());
    }

    @Test
    void warningWhenGazeCriticalButOffRoadLow() {
        var result = service.detectDistraction(
                dms(Map.of("gazeDeviation", 40.0, "offRoadDuration", 1.0)));

        assertTrue(result.isOk());
        assertEquals(RiskLevel.L2_WARNING, result.unwrap().riskLevel());
    }

    @Test
    void normalWhenWithinRange() {
        var result = service.detectDistraction(
                dms(Map.of("gazeDeviation", 10.0, "offRoadDuration", 0.0)));

        assertTrue(result.isOk());
        assertNull(result.unwrap().riskLevel());
        assertEquals("Within normal range", result.unwrap().summary());
    }
}
