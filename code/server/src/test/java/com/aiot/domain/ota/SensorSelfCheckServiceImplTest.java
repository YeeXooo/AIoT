package com.aiot.domain.ota;

import com.aiot.domain.event.CameraOcclusionDetectedEvent;
import com.aiot.domain.event.CameraOcclusionRemovedEvent;
import com.aiot.domain.event.DomainEventPublisher;
import com.aiot.domain.model.SensorStatus;
import com.aiot.domain.model.Vehicle;
import com.aiot.domain.port.CameraOcclusionDetectionPort;
import com.aiot.domain.repository.VehicleRepository;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.VehicleId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SensorSelfCheckServiceImplTest {

    @Mock private DomainEventPublisher eventPublisher;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private CameraOcclusionDetectionPort cameraOcclusionDetectionPort;

    private SensorSelfCheckServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SensorSelfCheckServiceImpl(eventPublisher, vehicleRepository, cameraOcclusionDetectionPort);
    }

    @Test
    void runSelfCheckReturnsErrorForMissingVehicle() {
        VehicleId vid = new VehicleId("v-none");
        when(vehicleRepository.findById(vid.id())).thenReturn(Optional.empty());

        Result<SensorSelfCheckService.SelfCheckResult, AppError> result = service.runSelfCheck(vid);

        assertTrue(result.isErr());
        assertEquals("NotFound", result.unwrapErr().code());
    }

    @Test
    void runSelfCheckReturnsStatusesForVehicleWithOnlineSensors() {
        Vehicle vehicle = Vehicle.register("京A12345", "VIN001", "SN001");
        vehicle.updateSensorStatus("cam1", SensorStatus.ONLINE);
        vehicle.updateSensorStatus("radar1", SensorStatus.ONLINE);
        when(vehicleRepository.findById(vehicle.vehicleId().id())).thenReturn(Optional.of(vehicle));

        Result<SensorSelfCheckService.SelfCheckResult, AppError> result = service.runSelfCheck(vehicle.vehicleId());

        assertTrue(result.isOk());
        assertEquals(2, result.unwrap().sensorStatuses().size());
        assertTrue(result.unwrap().occludedSensors().isEmpty());
    }

    @Test
    void runSelfCheckIdentifiesFaultSensorsAsOccluded() {
        Vehicle vehicle = Vehicle.register("京A12345", "VIN001", "SN001");
        vehicle.updateSensorStatus("cam1", SensorStatus.FAULT);
        vehicle.updateSensorStatus("radar1", SensorStatus.ONLINE);
        when(vehicleRepository.findById(vehicle.vehicleId().id())).thenReturn(Optional.of(vehicle));

        Result<SensorSelfCheckService.SelfCheckResult, AppError> result = service.runSelfCheck(vehicle.vehicleId());

        assertTrue(result.isOk());
        assertEquals(1, result.unwrap().occludedSensors().size());
        assertTrue(result.unwrap().occludedSensors().contains("cam1"));
    }

    @Test
    void runSelfCheckIdentifiesOfflineSensorsAsOccluded() {
        Vehicle vehicle = Vehicle.register("京A12345", "VIN001", "SN001");
        vehicle.updateSensorStatus("cam1", SensorStatus.OFFLINE);
        vehicle.updateSensorStatus("radar1", SensorStatus.ONLINE);
        when(vehicleRepository.findById(vehicle.vehicleId().id())).thenReturn(Optional.of(vehicle));

        Result<SensorSelfCheckService.SelfCheckResult, AppError> result = service.runSelfCheck(vehicle.vehicleId());

        assertTrue(result.isOk());
        assertEquals(1, result.unwrap().occludedSensors().size());
        assertTrue(result.unwrap().occludedSensors().contains("cam1"));
    }

    @Test
    void onOcclusionDetectedPublishesEvent() {
        long timestamp = System.currentTimeMillis();

        service.onOcclusionDetected("v1", "cam1", timestamp);

        verify(eventPublisher).publish(any(CameraOcclusionDetectedEvent.class));
    }

    @Test
    void onOcclusionRemovedPublishesEvent() {
        long timestamp = System.currentTimeMillis();

        service.onOcclusionRemoved("v1", "cam1", timestamp);

        verify(eventPublisher).publish(any(CameraOcclusionRemovedEvent.class));
    }
}
