package com.aiot.infra.adapter;

import com.aiot.domain.port.CameraOcclusionDetectionPort.OcclusionDetectedSignal;
import com.aiot.domain.port.CameraOcclusionDetectionPort.OcclusionRemovedSignal;
import com.aiot.domain.port.CameraOcclusionDetectionPort.OcclusionType;
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
class CameraOcclusionDetectionAdapterTest {

    @Mock
    private CameraOcclusionDetectionAdapter.CameraOcclusionCallback callback;

    private CameraOcclusionDetectionAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new CameraOcclusionDetectionAdapter();
        adapter.setCallback(callback);
    }

    @Test
    void simulateOcclusionDetected_shouldInvokeCallback() {
        adapter.simulateOcclusionDetected("cam-1", OcclusionType.PHYSICAL_COVER);

        ArgumentCaptor<OcclusionDetectedSignal> captor = ArgumentCaptor.forClass(OcclusionDetectedSignal.class);
        verify(callback).onOcclusionDetected(captor.capture());
        assertEquals("cam-1", captor.getValue().sensorId());
        assertEquals(OcclusionType.PHYSICAL_COVER, captor.getValue().occlusionType());
        assertNotNull(captor.getValue().timestamp());
    }

    @Test
    void simulateOcclusionRemoved_withoutPriorOcclusion_shouldInvokeCallbackWithNullDuration() {
        adapter.simulateOcclusionRemoved("cam-1");

        ArgumentCaptor<OcclusionRemovedSignal> captor = ArgumentCaptor.forClass(OcclusionRemovedSignal.class);
        verify(callback).onOcclusionRemoved(captor.capture());
        assertEquals("cam-1", captor.getValue().sensorId());
        assertNull(captor.getValue().durationMillis());
    }

    @Test
    void onOcclusionRemoved_afterOcclusionDetected_shouldNotThrow() {
        OcclusionDetectedSignal detected = new OcclusionDetectedSignal(
                Instant.now(), "cam-1", OcclusionType.ADHESIVE);
        adapter.onOcclusionDetected(detected);

        OcclusionRemovedSignal removed = new OcclusionRemovedSignal(
                Instant.now(), "cam-1", null);

        assertDoesNotThrow(() -> adapter.onOcclusionRemoved(removed));

        ArgumentCaptor<OcclusionRemovedSignal> captor = ArgumentCaptor.forClass(OcclusionRemovedSignal.class);
        verify(callback).onOcclusionRemoved(captor.capture());
        assertEquals("cam-1", captor.getValue().sensorId());
    }

    @Test
    void onOcclusionRemoved_withExplicitDuration_shouldUseProvidedDuration() {
        OcclusionRemovedSignal removed = new OcclusionRemovedSignal(
                Instant.now(), "cam-1", 5000L);
        adapter.onOcclusionRemoved(removed);

        ArgumentCaptor<OcclusionRemovedSignal> captor = ArgumentCaptor.forClass(OcclusionRemovedSignal.class);
        verify(callback).onOcclusionRemoved(captor.capture());
        assertEquals(5000L, captor.getValue().durationMillis());
    }

    @Test
    void onOcclusionDetected_shouldNotThrowWhenCallbackIsNull() {
        CameraOcclusionDetectionAdapter adapterWithoutCallback = new CameraOcclusionDetectionAdapter();

        assertDoesNotThrow(() -> adapterWithoutCallback.onOcclusionDetected(
                new OcclusionDetectedSignal(Instant.now(), "cam-1", OcclusionType.UNKNOWN)));
    }

    @Test
    void onOcclusionRemoved_shouldNotThrowWhenCallbackIsNull() {
        adapter.setCallback(null);

        assertDoesNotThrow(() -> adapter.onOcclusionRemoved(
                new OcclusionRemovedSignal(Instant.now(), "cam-1", 1000L)));
    }

    @Test
    void simulateOcclusionDetected_withUnknownType_shouldInvokeCallback() {
        adapter.simulateOcclusionDetected("cam-2", OcclusionType.UNKNOWN);

        ArgumentCaptor<OcclusionDetectedSignal> captor = ArgumentCaptor.forClass(OcclusionDetectedSignal.class);
        verify(callback).onOcclusionDetected(captor.capture());
        assertEquals(OcclusionType.UNKNOWN, captor.getValue().occlusionType());
    }

    @Test
    void simulateOcclusionRemoved_shouldResetLastOcclusionTime() {
        adapter.simulateOcclusionDetected("cam-1", OcclusionType.PHYSICAL_COVER);
        adapter.simulateOcclusionRemoved("cam-1");

        adapter.simulateOcclusionRemoved("cam-1");

        verify(callback, times(2)).onOcclusionRemoved(any());
        ArgumentCaptor<OcclusionRemovedSignal> captor = ArgumentCaptor.forClass(OcclusionRemovedSignal.class);
        verify(callback, times(2)).onOcclusionRemoved(captor.capture());
        assertNull(captor.getValue().durationMillis());
    }
}
