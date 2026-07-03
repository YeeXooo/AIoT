package com.aiot.domain.ota;

import com.aiot.domain.event.DomainEventPublisher;
import com.aiot.domain.event.OTAUpgradeCompletedEvent;
import com.aiot.domain.event.OTAUpgradeFailedEvent;
import com.aiot.domain.event.OTAUpgradeRolledBackEvent;
import com.aiot.domain.event.OTAUpgradeStartedEvent;
import com.aiot.domain.model.OTAUpgradeStatus;
import com.aiot.domain.model.OTAVersion;
import com.aiot.domain.model.UpgradeStage;
import com.aiot.domain.model.Vehicle;
import com.aiot.domain.port.OTADeliveryPort;
import com.aiot.domain.repository.VehicleRepository;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.VehicleId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OTAUpdateServiceImplTest {

    @Mock private VehicleRepository vehicleRepository;
    @Mock private DomainEventPublisher eventPublisher;
    @Mock private OTADeliveryPort otaDeliveryPort;

    private OTAUpdateServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OTAUpdateServiceImpl(vehicleRepository, eventPublisher, otaDeliveryPort);
    }

    @Test
    void initiateUpgradeReturnsErrorForMissingVehicle() {
        VehicleId vid = new VehicleId("v-none");
        OTAVersion version = OTAVersion.of("2.0.0", "", "");
        when(vehicleRepository.findById(vid.id())).thenReturn(Optional.empty());

        Result<Void, AppError> result = service.initiateUpgrade(vid, version);

        assertTrue(result.isErr());
        assertEquals("NotFound", result.unwrapErr().code());
    }

    @Test
    void initiateUpgradeReturnsErrorWhenUpgradeInProgress() {
        Vehicle vehicle = Vehicle.register("京A12345", "VIN001", "SN001");
        VehicleId vid = vehicle.vehicleId();
        OTAVersion version = OTAVersion.of("2.0.0", "", "");
        vehicle.updateOTAUpgradeStatus(OTAUpgradeStatus.init(version, java.time.Instant.now()));
        when(vehicleRepository.findById(vid.id())).thenReturn(Optional.of(vehicle));

        Result<Void, AppError> result = service.initiateUpgrade(vid, version);

        assertTrue(result.isErr());
        assertEquals("UpgradeInProgress", result.unwrapErr().code());
    }

    @Test
    void initiateUpgradeSucceedsAndPublishesStartedEvent() throws Exception {
        Vehicle vehicle = Vehicle.register("京A12345", "VIN001", "SN001");
        VehicleId vid = vehicle.vehicleId();
        OTAVersion version = OTAVersion.of("2.0.0", "", "abcdef");
        when(vehicleRepository.findById(vid.id())).thenReturn(Optional.of(vehicle));

        Result<Void, AppError> result = service.initiateUpgrade(vid, version);

        assertTrue(result.isOk());
        verify(eventPublisher).publish(any(OTAUpgradeStartedEvent.class));
        verify(vehicleRepository).save(vehicle);
    }

    @Test
    void initiateUpgradeRollsBackOnDeliveryFailure() throws Exception {
        Vehicle vehicle = Vehicle.register("京A12345", "VIN001", "SN001");
        VehicleId vid = vehicle.vehicleId();
        OTAVersion version = OTAVersion.of("2.0.0", "", "abcdef");
        when(vehicleRepository.findById(vid.id())).thenReturn(Optional.of(vehicle));
        doThrow(new OTADeliveryPort.OTADeliveryException.TransmissionFailedException("network error"))
                .when(otaDeliveryPort).deliverPackage(any(), any(), any());

        Result<Void, AppError> result = service.initiateUpgrade(vid, version);

        assertTrue(result.isErr());
        verify(eventPublisher).publish(any(OTAUpgradeRolledBackEvent.class));
        verify(vehicleRepository, times(2)).save(vehicle);
    }

    @Test
    void handleTransferProgressReturnsErrorForMissingVehicle() {
        VehicleId vid = new VehicleId("v-none");
        when(vehicleRepository.findById(vid.id())).thenReturn(Optional.empty());

        Result<UpgradeStage, AppError> result = service.handleTransferProgress(vid, 100, 1000, "IN_PROGRESS");

        assertTrue(result.isErr());
        assertEquals("NotFound", result.unwrapErr().code());
    }

    @Test
    void handleTransferProgressReturnsErrorWhenNoUpgradeInProgress() {
        Vehicle vehicle = Vehicle.register("京A12345", "VIN001", "SN001");
        VehicleId vid = vehicle.vehicleId();
        when(vehicleRepository.findById(vid.id())).thenReturn(Optional.of(vehicle));

        Result<UpgradeStage, AppError> result = service.handleTransferProgress(vid, 100, 1000, "IN_PROGRESS");

        assertTrue(result.isErr());
        assertEquals("InvalidState", result.unwrapErr().code());
    }

    @Test
    void handleTransferProgressCompletedMovesToVerifying() {
        Vehicle vehicle = Vehicle.register("京A12345", "VIN001", "SN001");
        VehicleId vid = vehicle.vehicleId();
        OTAVersion version = OTAVersion.of("2.0.0", "", "");
        vehicle.updateOTAUpgradeStatus(OTAUpgradeStatus.init(version, java.time.Instant.now()));
        when(vehicleRepository.findById(vid.id())).thenReturn(Optional.of(vehicle));

        Result<UpgradeStage, AppError> result = service.handleTransferProgress(vid, 1000, 1000, "COMPLETED");

        assertTrue(result.isOk());
        assertEquals(UpgradeStage.VERIFYING, result.unwrap());
    }

    @Test
    void handleTransferProgressFailedMovesToRolledBack() {
        Vehicle vehicle = Vehicle.register("京A12345", "VIN001", "SN001");
        VehicleId vid = vehicle.vehicleId();
        OTAVersion version = OTAVersion.of("2.0.0", "", "");
        vehicle.updateOTAUpgradeStatus(OTAUpgradeStatus.init(version, java.time.Instant.now()));
        when(vehicleRepository.findById(vid.id())).thenReturn(Optional.of(vehicle));

        Result<UpgradeStage, AppError> result = service.handleTransferProgress(vid, 500, 1000, "FAILED");

        assertTrue(result.isOk());
        assertEquals(UpgradeStage.ROLLED_BACK, result.unwrap());
        verify(eventPublisher).publish(any(OTAUpgradeFailedEvent.class));
        verify(eventPublisher).publish(any(OTAUpgradeRolledBackEvent.class));
    }

    @Test
    void handleTransferProgressInProgressReturnsTransmitting() {
        Vehicle vehicle = Vehicle.register("京A12345", "VIN001", "SN001");
        VehicleId vid = vehicle.vehicleId();
        OTAVersion version = OTAVersion.of("2.0.0", "", "");
        vehicle.updateOTAUpgradeStatus(OTAUpgradeStatus.init(version, java.time.Instant.now()));
        when(vehicleRepository.findById(vid.id())).thenReturn(Optional.of(vehicle));

        Result<UpgradeStage, AppError> result = service.handleTransferProgress(vid, 500, 1000, "IN_PROGRESS");

        assertTrue(result.isOk());
        assertEquals(UpgradeStage.TRANSMITTING, result.unwrap());
    }

    @Test
    void handleVerificationResultReturnsErrorForMissingVehicle() {
        VehicleId vid = new VehicleId("v-none");
        when(vehicleRepository.findById(vid.id())).thenReturn(Optional.empty());

        Result<UpgradeStage, AppError> result = service.handleVerificationResult(vid, true);

        assertTrue(result.isErr());
    }

    @Test
    void handleVerificationResultInvalidChecksumRollsBack() {
        Vehicle vehicle = Vehicle.register("京A12345", "VIN001", "SN001");
        VehicleId vid = vehicle.vehicleId();
        OTAVersion version = OTAVersion.of("2.0.0", "", "");
        vehicle.updateOTAUpgradeStatus(OTAUpgradeStatus.init(version, java.time.Instant.now()));
        when(vehicleRepository.findById(vid.id())).thenReturn(Optional.of(vehicle));

        Result<UpgradeStage, AppError> result = service.handleVerificationResult(vid, false);

        assertTrue(result.isOk());
        assertEquals(UpgradeStage.ROLLED_BACK, result.unwrap());
        verify(eventPublisher).publish(any(OTAUpgradeFailedEvent.class));
        verify(eventPublisher).publish(any(OTAUpgradeRolledBackEvent.class));
    }

    @Test
    void handleVerificationResultValidChecksumMovesToUpgrading() {
        Vehicle vehicle = Vehicle.register("京A12345", "VIN001", "SN001");
        VehicleId vid = vehicle.vehicleId();
        OTAVersion version = OTAVersion.of("2.0.0", "", "");
        vehicle.updateOTAUpgradeStatus(OTAUpgradeStatus.init(version, java.time.Instant.now()));
        when(vehicleRepository.findById(vid.id())).thenReturn(Optional.of(vehicle));

        Result<UpgradeStage, AppError> result = service.handleVerificationResult(vid, true);

        assertTrue(result.isOk());
        assertEquals(UpgradeStage.UPGRADING, result.unwrap());
    }

    @Test
    void handleFirmwareFlashResultSuccessCompletesUpgrade() {
        Vehicle vehicle = Vehicle.register("京A12345", "VIN001", "SN001");
        VehicleId vid = vehicle.vehicleId();
        OTAVersion version = OTAVersion.of("2.0.0", "", "");
        vehicle.updateOTAUpgradeStatus(OTAUpgradeStatus.init(version, java.time.Instant.now()));
        when(vehicleRepository.findById(vid.id())).thenReturn(Optional.of(vehicle));

        Result<UpgradeStage, AppError> result = service.handleFirmwareFlashResult(vid, true);

        assertTrue(result.isOk());
        assertEquals(UpgradeStage.COMPLETED, result.unwrap());
        verify(eventPublisher).publish(any(OTAUpgradeCompletedEvent.class));
    }

    @Test
    void handleFirmwareFlashResultFailureRollsBack() {
        Vehicle vehicle = Vehicle.register("京A12345", "VIN001", "SN001");
        VehicleId vid = vehicle.vehicleId();
        OTAVersion version = OTAVersion.of("2.0.0", "", "");
        vehicle.updateOTAUpgradeStatus(OTAUpgradeStatus.init(version, java.time.Instant.now()));
        when(vehicleRepository.findById(vid.id())).thenReturn(Optional.of(vehicle));

        Result<UpgradeStage, AppError> result = service.handleFirmwareFlashResult(vid, false);

        assertTrue(result.isOk());
        assertEquals(UpgradeStage.ROLLED_BACK, result.unwrap());
        verify(eventPublisher).publish(any(OTAUpgradeFailedEvent.class));
        verify(eventPublisher).publish(any(OTAUpgradeRolledBackEvent.class));
    }
}
