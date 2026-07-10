package com.aiot.domain.risk;

import com.aiot.domain.event.DomainEventPublisher;
import com.aiot.domain.model.DetectionWindow;
import com.aiot.domain.model.SensorReading;
import com.aiot.domain.shared.TripId;
import com.aiot.domain.shared.VehicleId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LifeDetectionServiceImplTest {

    @Mock
    private DomainEventPublisher eventPublisher;

    private LifeDetectionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LifeDetectionServiceImpl(eventPublisher);
    }

    private SensorReading radar(Map<String, Double> values) {
        return new SensorReading(SensorReading.SensorType.MILLIMETER_WAVE_RADAR, Instant.now(),
                new TripId("trip-1"), values);
    }

    private DetectionWindow window(Duration remaining) {
        return DetectionWindow.create(remaining, Instant.now(), Duration.ofSeconds(1));
    }

    @Test
    void rejectsNullRadarSignal() {
        var result = service.evaluateLifeDetection(null, window(Duration.ofMinutes(1)),
                new VehicleId("v001"));

        assertTrue(result.isErr());
        assertEquals("ValidationFailed", result.unwrapErr().code());
    }

    @Test
    void rejectsNullWindow() {
        var result = service.evaluateLifeDetection(radar(Map.of("m1", 0.5)), null,
                new VehicleId("v001"));

        assertTrue(result.isErr());
        assertEquals("ValidationFailed", result.unwrapErr().code());
    }

    @Test
    void returnsWindowWithoutEventWhenExpired() {
        DetectionWindow expired = window(Duration.ofMinutes(1)).tick(Duration.ofMinutes(2));
        assertTrue(expired.isExpired());

        var result = service.evaluateLifeDetection(radar(Map.of("m1", 0.9)), expired,
                new VehicleId("v001"));

        assertTrue(result.isOk());
        assertNull(result.unwrap().detectedEvent());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void doesNotIncrementWhenEnergyBelowThreshold() {
        var result = service.evaluateLifeDetection(
                radar(Map.of("m1", 0.05, "m2", 0.05)), window(Duration.ofMinutes(5)),
                new VehicleId("v001"));

        assertTrue(result.isOk());
        assertEquals(0, result.unwrap().updatedWindow().getMicroMovementCount());
        assertNull(result.unwrap().detectedEvent());
    }

    @Test
    void incrementsWhenEnergyAboveThreshold() {
        var result = service.evaluateLifeDetection(
                radar(Map.of("m1", 0.9, "m2", 0.9)), window(Duration.ofMinutes(5)),
                new VehicleId("v001"));

        assertTrue(result.isOk());
        assertEquals(1, result.unwrap().updatedWindow().getMicroMovementCount());
    }

    @Test
    void publishesEventWhenMovementCountReachesThreshold() {
        DetectionWindow prefilled = window(Duration.ofMinutes(5))
                .incrementCount().incrementCount();

        var result = service.evaluateLifeDetection(
                radar(Map.of("m1", 0.9, "m2", 0.9)), prefilled, new VehicleId("v001"));

        assertTrue(result.isOk());
        assertNotNull(result.unwrap().detectedEvent());
        assertEquals(3, result.unwrap().updatedWindow().getMicroMovementCount());
        verify(eventPublisher, times(1)).publish(any());
    }

    @Test
    void handlesEmptyRadarValues() {
        var result = service.evaluateLifeDetection(
                radar(Map.of()), window(Duration.ofMinutes(5)), new VehicleId("v001"));

        assertTrue(result.isOk());
        assertEquals(0, result.unwrap().updatedWindow().getMicroMovementCount());
    }
}
