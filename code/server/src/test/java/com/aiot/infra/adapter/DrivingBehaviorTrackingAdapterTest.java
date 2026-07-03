package com.aiot.infra.adapter;

import com.aiot.domain.port.DrivingBehaviorTrackingPort.HardBrakingEvent;
import com.aiot.domain.port.DrivingBehaviorTrackingPort.HardAccelerationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DrivingBehaviorTrackingAdapterTest {

    @Mock
    private DrivingBehaviorTrackingAdapter.DrivingBehaviorTrackingCallback callback;

    private DrivingBehaviorTrackingAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new DrivingBehaviorTrackingAdapter(3.5, 3.0);
        adapter.setCallback(callback);
    }

    @Test
    void simulateHardBraking_shouldInvokeCallbackWhenAboveThreshold() {
        adapter.simulateHardBraking(4.0);

        ArgumentCaptor<HardBrakingEvent> captor = ArgumentCaptor.forClass(HardBrakingEvent.class);
        verify(callback).onHardBraking(captor.capture());
        assertEquals(4.0, captor.getValue().deceleration());
        assertNotNull(captor.getValue().timestamp());
    }

    @Test
    void simulateHardBraking_shouldNotInvokeCallbackWhenBelowThreshold() {
        adapter.simulateHardBraking(2.0);

        verifyNoInteractions(callback);
    }

    @Test
    void simulateHardBraking_shouldInvokeCallbackWhenExactlyAtThreshold() {
        adapter.simulateHardBraking(3.5);

        verify(callback).onHardBraking(any());
    }

    @Test
    void simulateHardAcceleration_shouldInvokeCallbackWhenAboveThreshold() {
        adapter.simulateHardAcceleration(3.5);

        ArgumentCaptor<HardAccelerationEvent> captor = ArgumentCaptor.forClass(HardAccelerationEvent.class);
        verify(callback).onHardAcceleration(captor.capture());
        assertEquals(3.5, captor.getValue().acceleration());
        assertNotNull(captor.getValue().timestamp());
    }

    @Test
    void simulateHardAcceleration_shouldNotInvokeCallbackWhenBelowThreshold() {
        adapter.simulateHardAcceleration(2.5);

        verifyNoInteractions(callback);
    }

    @Test
    void onHardBrakingDetected_shouldNotThrowWhenCallbackIsNull() {
        DrivingBehaviorTrackingAdapter adapterWithoutCallback = new DrivingBehaviorTrackingAdapter();

        assertDoesNotThrow(() -> adapterWithoutCallback.onHardBrakingDetected(
                new HardBrakingEvent(Instant.now(), 5.0)));
    }

    @Test
    void onHardAccelerationDetected_shouldNotThrowWhenCallbackIsNull() {
        adapter.setCallback(null);

        assertDoesNotThrow(() -> adapter.onHardAccelerationDetected(
                new HardAccelerationEvent(Instant.now(), 4.0)));
    }

    @Test
    void defaultConstructor_shouldUseDefaultThresholds() {
        DrivingBehaviorTrackingAdapter defaultAdapter = new DrivingBehaviorTrackingAdapter();

        defaultAdapter.setCallback(callback);
        defaultAdapter.simulateHardBraking(3.6);
        verify(callback).onHardBraking(any());

        defaultAdapter.simulateHardAcceleration(3.1);
        verify(callback).onHardAcceleration(any());
    }
}
