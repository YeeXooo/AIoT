package com.aiot.domain.intervention;

import com.aiot.domain.event.DomainEventPublisher;
import com.aiot.domain.event.EmergencyActivatedEvent;
import com.aiot.domain.model.PhysiologicalSnapshot;
import com.aiot.domain.model.TimeRange;
import com.aiot.domain.model.VehicleStateSnapshot;
import com.aiot.domain.port.BufferException;
import com.aiot.domain.port.PhysiologicalDataBuffer;
import com.aiot.domain.port.VehicleStateBuffer;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmergencyResponseServiceImplTest {

    @Mock private VehicleStateBuffer vehicleStateBuffer;
    @Mock private PhysiologicalDataBuffer physiologicalDataBuffer;
    @Mock private DomainEventPublisher eventPublisher;

    private EmergencyResponseServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EmergencyResponseServiceImpl(vehicleStateBuffer, physiologicalDataBuffer, eventPublisher);
    }

    @Test
    void determineDisabilityNoCollisionWhenAccelerationAndImpactBelowThreshold() {
        EmergencyResponseService.CollisionImpactSignal signal =
                new EmergencyResponseService.CollisionImpactSignal(10.0, 0.1, System.currentTimeMillis(), "d1");

        Result<EmergencyResponseService.DisabilityAssessment, AppError> result = service.determineDisability(signal);

        assertTrue(result.isOk());
        assertEquals("No collision detected", result.unwrap().conclusion());
        assertEquals(0.0, result.unwrap().confidence());
    }

    @Test
    void determineDisabilityHighAccelerationTriggersAssessment() throws Exception {
        EmergencyResponseService.CollisionImpactSignal signal =
                new EmergencyResponseService.CollisionImpactSignal(90.0, 0.5, System.currentTimeMillis(), "d1");
        when(vehicleStateBuffer.getSnapshots(isNull(), any())).thenReturn(Collections.emptyList());
        when(physiologicalDataBuffer.getReadings(isNull(), any())).thenReturn(Collections.emptyList());

        Result<EmergencyResponseService.DisabilityAssessment, AppError> result = service.determineDisability(signal);

        assertTrue(result.isOk());
    }

    @Test
    void determineDisabilityHighImpactTriggersAssessment() throws Exception {
        EmergencyResponseService.CollisionImpactSignal signal =
                new EmergencyResponseService.CollisionImpactSignal(50.0, 0.9, System.currentTimeMillis(), "d1");
        when(vehicleStateBuffer.getSnapshots(isNull(), any())).thenReturn(Collections.emptyList());
        when(physiologicalDataBuffer.getReadings(isNull(), any())).thenReturn(Collections.emptyList());

        Result<EmergencyResponseService.DisabilityAssessment, AppError> result = service.determineDisability(signal);

        assertTrue(result.isOk());
    }

    @Test
    void determineDisabilityDriverLikelyDisabledWithAbnormalPhysiologicalData() throws Exception {
        EmergencyResponseService.CollisionImpactSignal signal =
                new EmergencyResponseService.CollisionImpactSignal(90.0, 0.8, System.currentTimeMillis(), "d1");
        List<PhysiologicalSnapshot> physio = List.of(
                new PhysiologicalSnapshot(Instant.now(), 30, 80.0, null, null, null, null, null, null)
        );
        when(vehicleStateBuffer.getSnapshots(isNull(), any())).thenReturn(Collections.emptyList());
        when(physiologicalDataBuffer.getReadings(isNull(), any())).thenReturn(physio);

        Result<EmergencyResponseService.DisabilityAssessment, AppError> result = service.determineDisability(signal);

        assertTrue(result.isOk());
        assertEquals("Driver likely disabled", result.unwrap().conclusion());
        verify(eventPublisher).publish(any(EmergencyActivatedEvent.class));
    }

    @Test
    void determineDisabilityDriverResponsiveWithNormalPhysiologicalData() throws Exception {
        EmergencyResponseService.CollisionImpactSignal signal =
                new EmergencyResponseService.CollisionImpactSignal(90.0, 0.8, System.currentTimeMillis(), "d1");
        List<PhysiologicalSnapshot> physio = List.of(
                new PhysiologicalSnapshot(Instant.now(), 80, 98.0, null, null, null, null, null, null)
        );
        when(vehicleStateBuffer.getSnapshots(isNull(), any())).thenReturn(Collections.emptyList());
        when(physiologicalDataBuffer.getReadings(isNull(), any())).thenReturn(physio);

        Result<EmergencyResponseService.DisabilityAssessment, AppError> result = service.determineDisability(signal);

        assertTrue(result.isOk());
        assertEquals("Driver responsive", result.unwrap().conclusion());
    }

    @Test
    void determineDisabilityReturnsErrorWhenBufferRetrievalFails() throws Exception {
        EmergencyResponseService.CollisionImpactSignal signal =
                new EmergencyResponseService.CollisionImpactSignal(90.0, 0.8, System.currentTimeMillis(), "d1");
        when(vehicleStateBuffer.getSnapshots(isNull(), any())).thenThrow(new BufferException.BufferUnavailableException("buffer unavailable"));

        Result<EmergencyResponseService.DisabilityAssessment, AppError> result = service.determineDisability(signal);

        assertTrue(result.isErr());
        assertEquals("InvalidState", result.unwrapErr().code());
        assertTrue(result.unwrapErr().message().contains("buffer unavailable"));
    }

    @Test
    void determineDisabilityPhysiologicalBufferExceptionReturnsError() throws Exception {
        EmergencyResponseService.CollisionImpactSignal signal =
                new EmergencyResponseService.CollisionImpactSignal(90.0, 0.8, System.currentTimeMillis(), "d1");
        when(vehicleStateBuffer.getSnapshots(isNull(), any())).thenReturn(Collections.emptyList());
        when(physiologicalDataBuffer.getReadings(isNull(), any())).thenThrow(new BufferException.WindowNotCoveredException("window not covered"));

        Result<EmergencyResponseService.DisabilityAssessment, AppError> result = service.determineDisability(signal);

        assertTrue(result.isErr());
    }
}
