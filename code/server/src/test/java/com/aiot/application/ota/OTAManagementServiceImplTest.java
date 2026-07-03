package com.aiot.application.ota;

import com.aiot.domain.model.OTAUpgradeStatus;
import com.aiot.domain.model.OTAVersion;
import com.aiot.domain.model.UpgradeStage;
import com.aiot.domain.model.Vehicle;
import com.aiot.domain.ota.OTAUpdateService;
import com.aiot.domain.port.OTADeliveryPort;
import com.aiot.domain.repository.VehicleRepository;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.VehicleId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OTAManagementServiceImplTest {

    @Mock
    private OTAUpdateService otaUpdateService;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private OTADeliveryPort otaDeliveryPort;

    private OTAManagementServiceImpl service;

    private static final VehicleId VEHICLE_ID = new VehicleId("vehicle-1");
    private static final OTAVersion TARGET_VERSION = OTAVersion.of("2.0.0", "model-A", "sha256");

    @BeforeEach
    void setUp() {
        service = new OTAManagementServiceImpl(otaUpdateService, vehicleRepository, otaDeliveryPort);
    }

    private Vehicle createVehicle() {
        return Vehicle.reconstitute(VEHICLE_ID, "京A12345", "VIN123", "SN123",
                "fleet-1", "1.0.0", 1, LocalDateTime.now(), LocalDateTime.now());
    }

    private Vehicle createVehicleWithStatus(UpgradeStage stage) {
        Vehicle v = createVehicle();
        OTAUpgradeStatus status = OTAUpgradeStatus.init(
                OTAVersion.of("2.0.0", "", ""), Instant.now());
        if (stage != UpgradeStage.PENDING) {
            status = status.transition(stage, 0, Instant.now());
        }
        v.updateOTAUpgradeStatus(status);
        return v;
    }

    // ========== createUpgradeTask ==========

    @Test
    void createUpgradeTaskShouldSucceedForVehicleWithoutExistingUpgrade() {
        Vehicle vehicle = createVehicle();
        when(vehicleRepository.findById(VEHICLE_ID.id())).thenReturn(Optional.of(vehicle));
        when(otaUpdateService.initiateUpgrade(VEHICLE_ID, TARGET_VERSION))
                .thenReturn(Result.ok(null));

        Result<IOTAManagementService.CreateTaskResponse, AppError> result =
                service.createUpgradeTask(VEHICLE_ID, TARGET_VERSION, "idem-1");

        assertTrue(result.isOk());
        IOTAManagementService.CreateTaskResponse resp = result.unwrap();
        assertNotNull(resp.taskId());
        assertEquals("PENDING", resp.status());
        verify(otaUpdateService).initiateUpgrade(VEHICLE_ID, TARGET_VERSION);
    }

    @Test
    void createUpgradeTaskShouldReturnErrWhenVehicleNotFound() {
        when(vehicleRepository.findById("unknown")).thenReturn(Optional.empty());

        Result<IOTAManagementService.CreateTaskResponse, AppError> result =
                service.createUpgradeTask(new VehicleId("unknown"), TARGET_VERSION, null);

        assertTrue(result.isErr());
        assertEquals("NotFound", result.unwrapErr().code());
        verify(otaUpdateService, never()).initiateUpgrade(any(), any());
    }

    @Test
    void createUpgradeTaskShouldReturnErrWhenUpgradeInProgress() {
        Vehicle vehicle = createVehicleWithStatus(UpgradeStage.TRANSMITTING);
        when(vehicleRepository.findById(VEHICLE_ID.id())).thenReturn(Optional.of(vehicle));

        Result<IOTAManagementService.CreateTaskResponse, AppError> result =
                service.createUpgradeTask(VEHICLE_ID, TARGET_VERSION, null);

        assertTrue(result.isErr());
        assertEquals("UpgradeInProgress", result.unwrapErr().code());
        verify(otaUpdateService, never()).initiateUpgrade(any(), any());
    }

    @Test
    void createUpgradeTaskShouldAllowNewTaskWhenPreviousCompleted() {
        Vehicle vehicle = createVehicleWithStatus(UpgradeStage.COMPLETED);
        when(vehicleRepository.findById(VEHICLE_ID.id())).thenReturn(Optional.of(vehicle));
        when(otaUpdateService.initiateUpgrade(VEHICLE_ID, TARGET_VERSION))
                .thenReturn(Result.ok(null));

        Result<IOTAManagementService.CreateTaskResponse, AppError> result =
                service.createUpgradeTask(VEHICLE_ID, TARGET_VERSION, null);

        assertTrue(result.isOk());
        verify(otaUpdateService).initiateUpgrade(VEHICLE_ID, TARGET_VERSION);
    }

    @Test
    void createUpgradeTaskShouldAllowNewTaskWhenPreviousRolledBack() {
        Vehicle vehicle = createVehicleWithStatus(UpgradeStage.ROLLED_BACK);
        when(vehicleRepository.findById(VEHICLE_ID.id())).thenReturn(Optional.of(vehicle));
        when(otaUpdateService.initiateUpgrade(VEHICLE_ID, TARGET_VERSION))
                .thenReturn(Result.ok(null));

        Result<IOTAManagementService.CreateTaskResponse, AppError> result =
                service.createUpgradeTask(VEHICLE_ID, TARGET_VERSION, null);

        assertTrue(result.isOk());
        verify(otaUpdateService).initiateUpgrade(VEHICLE_ID, TARGET_VERSION);
    }

    @Test
    void createUpgradeTaskShouldPropagateErrorFromInitiateUpgrade() {
        Vehicle vehicle = createVehicle();
        when(vehicleRepository.findById(VEHICLE_ID.id())).thenReturn(Optional.of(vehicle));
        AppError error = AppError.iotdaChannelFailure("network error");
        when(otaUpdateService.initiateUpgrade(VEHICLE_ID, TARGET_VERSION))
                .thenReturn(Result.err(error));

        Result<IOTAManagementService.CreateTaskResponse, AppError> result =
                service.createUpgradeTask(VEHICLE_ID, TARGET_VERSION, null);

        assertTrue(result.isErr());
        assertEquals("IoTDAChannelFailure", result.unwrapErr().code());
    }

    // ========== createUpgradeTasks ==========

    @Test
    void createUpgradeTasksShouldSucceedForAllVehicles() {
        Vehicle v1 = createVehicle();
        Vehicle v2 = Vehicle.reconstitute(new VehicleId("vehicle-2"), "京B67890", "VIN456", "SN456",
                "fleet-1", "1.0.0", 1, LocalDateTime.now(), LocalDateTime.now());
        when(vehicleRepository.findById("vehicle-1")).thenReturn(Optional.of(v1));
        when(vehicleRepository.findById("vehicle-2")).thenReturn(Optional.of(v2));
        when(otaUpdateService.initiateUpgrade(any(), eq(TARGET_VERSION)))
                .thenReturn(Result.ok(null));

        List<VehicleId> ids = List.of(VEHICLE_ID, new VehicleId("vehicle-2"));
        Result<IOTAManagementService.BatchCreateTasksResponse, AppError> result =
                service.createUpgradeTasks(ids, TARGET_VERSION);

        assertTrue(result.isOk());
        IOTAManagementService.BatchCreateTasksResponse resp = result.unwrap();
        assertEquals(2, resp.successCount());
        assertEquals(0, resp.failureCount());
        assertEquals(2, resp.results().size());
    }

    @Test
    void createUpgradeTasksShouldHandleMixedSuccessAndFailure() {
        Vehicle v1 = createVehicle();
        when(vehicleRepository.findById("vehicle-1")).thenReturn(Optional.of(v1));
        when(vehicleRepository.findById("vehicle-2")).thenReturn(Optional.empty());
        when(otaUpdateService.initiateUpgrade(VEHICLE_ID, TARGET_VERSION))
                .thenReturn(Result.ok(null));

        List<VehicleId> ids = List.of(VEHICLE_ID, new VehicleId("vehicle-2"));
        Result<IOTAManagementService.BatchCreateTasksResponse, AppError> result =
                service.createUpgradeTasks(ids, TARGET_VERSION);

        assertTrue(result.isOk());
        IOTAManagementService.BatchCreateTasksResponse resp = result.unwrap();
        assertEquals(1, resp.successCount());
        assertEquals(1, resp.failureCount());
        assertEquals(2, resp.results().size());
    }

    @Test
    void createUpgradeTasksShouldHandleAllFailures() {
        when(vehicleRepository.findById("vehicle-1")).thenReturn(Optional.empty());
        when(vehicleRepository.findById("vehicle-2")).thenReturn(Optional.empty());

        List<VehicleId> ids = List.of(VEHICLE_ID, new VehicleId("vehicle-2"));
        Result<IOTAManagementService.BatchCreateTasksResponse, AppError> result =
                service.createUpgradeTasks(ids, TARGET_VERSION);

        assertTrue(result.isOk());
        assertEquals(0, result.unwrap().successCount());
        assertEquals(2, result.unwrap().failureCount());
    }

    @Test
    void createUpgradeTasksShouldReturnOkForEmptyList() {
        Result<IOTAManagementService.BatchCreateTasksResponse, AppError> result =
                service.createUpgradeTasks(Collections.emptyList(), TARGET_VERSION);

        assertTrue(result.isOk());
        assertEquals(0, result.unwrap().successCount());
        assertEquals(0, result.unwrap().failureCount());
        assertTrue(result.unwrap().results().isEmpty());
    }

    // ========== queryUpgradeProgress ==========

    @Test
    void queryUpgradeProgressShouldReturnStatusWhenUpgradeExists() {
        Vehicle vehicle = createVehicleWithStatus(UpgradeStage.TRANSMITTING);
        when(vehicleRepository.findById(VEHICLE_ID.id())).thenReturn(Optional.of(vehicle));

        Result<IOTAManagementService.UpgradeProgressResponse, AppError> result =
                service.queryUpgradeProgress(VEHICLE_ID);

        assertTrue(result.isOk());
        IOTAManagementService.UpgradeProgressResponse resp = result.unwrap();
        assertEquals("TRANSMITTING", resp.stage());
        assertEquals("1.0.0", resp.currentVersion());
        assertEquals("2.0.0", resp.targetVersion());
    }

    @Test
    void queryUpgradeProgressShouldReturnCompletedWhenNoStatus() {
        Vehicle vehicle = createVehicle();
        when(vehicleRepository.findById(VEHICLE_ID.id())).thenReturn(Optional.of(vehicle));

        Result<IOTAManagementService.UpgradeProgressResponse, AppError> result =
                service.queryUpgradeProgress(VEHICLE_ID);

        assertTrue(result.isOk());
        assertEquals("COMPLETED", result.unwrap().stage());
        assertEquals(0L, result.unwrap().transferredBytes());
    }

    @Test
    void queryUpgradeProgressShouldReturnErrWhenVehicleNotFound() {
        when(vehicleRepository.findById("unknown")).thenReturn(Optional.empty());

        Result<IOTAManagementService.UpgradeProgressResponse, AppError> result =
                service.queryUpgradeProgress(new VehicleId("unknown"));

        assertTrue(result.isErr());
        assertEquals("NotFound", result.unwrapErr().code());
    }

    @Test
    void queryUpgradeProgressShouldReturnCurrentVersionFromVehicle() {
        Vehicle vehicle = createVehicle();
        when(vehicleRepository.findById(VEHICLE_ID.id())).thenReturn(Optional.of(vehicle));

        Result<IOTAManagementService.UpgradeProgressResponse, AppError> result =
                service.queryUpgradeProgress(VEHICLE_ID);

        assertTrue(result.isOk());
        assertEquals("1.0.0", result.unwrap().currentVersion());
        assertEquals("1.0.0", result.unwrap().targetVersion());
    }

    // ========== triggerRollback ==========

    @Test
    void triggerRollbackShouldSucceedForActiveUpgrade() {
        Vehicle vehicle = createVehicleWithStatus(UpgradeStage.TRANSMITTING);
        when(vehicleRepository.findById(VEHICLE_ID.id())).thenReturn(Optional.of(vehicle));

        Result<IOTAManagementService.RollbackResponse, AppError> result =
                service.triggerRollback(VEHICLE_ID);

        assertTrue(result.isOk());
        IOTAManagementService.RollbackResponse resp = result.unwrap();
        assertNotNull(resp.taskId());
        assertEquals("TRANSMITTING", resp.previousStage());
        assertEquals("1.0.0", resp.rolledBackVersion());

        ArgumentCaptor<Vehicle> captor = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).save(captor.capture());
        assertTrue(captor.getValue().otaUpgradeStatus().isPresent());
        assertEquals(UpgradeStage.ROLLED_BACK, captor.getValue().otaUpgradeStatus().get().getStage());
        verify(otaDeliveryPort).cancelDelivery(VEHICLE_ID);
    }

    @Test
    void triggerRollbackShouldReturnErrWhenVehicleNotFound() {
        when(vehicleRepository.findById("unknown")).thenReturn(Optional.empty());

        Result<IOTAManagementService.RollbackResponse, AppError> result =
                service.triggerRollback(new VehicleId("unknown"));

        assertTrue(result.isErr());
        assertEquals("NotFound", result.unwrapErr().code());
    }

    @Test
    void triggerRollbackShouldReturnErrWhenNoUpgradeInProgress() {
        Vehicle vehicle = createVehicle();
        when(vehicleRepository.findById(VEHICLE_ID.id())).thenReturn(Optional.of(vehicle));

        Result<IOTAManagementService.RollbackResponse, AppError> result =
                service.triggerRollback(VEHICLE_ID);

        assertTrue(result.isErr());
        assertEquals("InvalidState", result.unwrapErr().code());
        verify(otaDeliveryPort, never()).cancelDelivery(any());
    }

    @Test
    void triggerRollbackShouldPropagateExceptionFromDomainService() {
        Vehicle vehicle = createVehicleWithStatus(UpgradeStage.TRANSMITTING);
        when(vehicleRepository.findById(VEHICLE_ID.id())).thenReturn(Optional.of(vehicle));
        doThrow(new RuntimeException("persistence failure")).when(vehicleRepository).save(any());

        assertThrows(RuntimeException.class, () -> service.triggerRollback(VEHICLE_ID));
    }

    // ========== queryUpgradeHistory ==========

    @Test
    void queryUpgradeHistoryShouldReturnHistoryWhenStatusExists() {
        Vehicle vehicle = createVehicleWithStatus(UpgradeStage.COMPLETED);
        when(vehicleRepository.findById(VEHICLE_ID.id())).thenReturn(Optional.of(vehicle));

        Result<IOTAManagementService.UpgradeHistoryResponse, AppError> result =
                service.queryUpgradeHistory(VEHICLE_ID, 1, 10);

        assertTrue(result.isOk());
        IOTAManagementService.UpgradeHistoryResponse resp = result.unwrap();
        assertEquals(1, resp.totalCount());
        assertEquals(1, resp.items().size());
        assertEquals(VEHICLE_ID.id(), resp.items().get(0).vehicleId());
        assertEquals("COMPLETED", resp.items().get(0).status());
    }

    @Test
    void queryUpgradeHistoryShouldReturnEmptyWhenNoStatus() {
        Vehicle vehicle = createVehicle();
        when(vehicleRepository.findById(VEHICLE_ID.id())).thenReturn(Optional.of(vehicle));

        Result<IOTAManagementService.UpgradeHistoryResponse, AppError> result =
                service.queryUpgradeHistory(VEHICLE_ID, 1, 10);

        assertTrue(result.isOk());
        assertTrue(result.unwrap().items().isEmpty());
    }

    @Test
    void queryUpgradeHistoryShouldReturnErrWhenVehicleNotFound() {
        when(vehicleRepository.findById("unknown")).thenReturn(Optional.empty());

        Result<IOTAManagementService.UpgradeHistoryResponse, AppError> result =
                service.queryUpgradeHistory(new VehicleId("unknown"), 1, 10);

        assertTrue(result.isErr());
        assertEquals("NotFound", result.unwrapErr().code());
    }

    @Test
    void queryUpgradeHistoryShouldReturnEmptyWhenPageBeyondRange() {
        Vehicle vehicle = createVehicleWithStatus(UpgradeStage.COMPLETED);
        when(vehicleRepository.findById(VEHICLE_ID.id())).thenReturn(Optional.of(vehicle));

        Result<IOTAManagementService.UpgradeHistoryResponse, AppError> result =
                service.queryUpgradeHistory(VEHICLE_ID, 5, 10);

        assertTrue(result.isOk());
        assertTrue(result.unwrap().items().isEmpty());
    }

    @Test
    void queryUpgradeHistoryShouldIncludeTargetVersionInHistoryItem() {
        Vehicle vehicle = createVehicle();
        OTAUpgradeStatus status = OTAUpgradeStatus.init(
                OTAVersion.of("3.0.0", "", ""), Instant.now()).transition(
                UpgradeStage.UPGRADING, 1024, Instant.now());
        vehicle.updateOTAUpgradeStatus(status);
        when(vehicleRepository.findById(VEHICLE_ID.id())).thenReturn(Optional.of(vehicle));

        Result<IOTAManagementService.UpgradeHistoryResponse, AppError> result =
                service.queryUpgradeHistory(VEHICLE_ID, 1, 10);

        assertTrue(result.isOk());
        assertEquals("3.0.0", result.unwrap().items().get(0).targetVersion());
        assertEquals("UPGRADING", result.unwrap().items().get(0).status());
    }

    // ========== cancelUpgradeTask ==========

    @Test
    void cancelUpgradeTaskShouldCancelActiveUpgrade() {
        Vehicle vehicle = createVehicleWithStatus(UpgradeStage.TRANSMITTING);
        when(vehicleRepository.findById(VEHICLE_ID.id())).thenReturn(Optional.of(vehicle));

        Result<IOTAManagementService.CancelTaskResponse, AppError> result =
                service.cancelUpgradeTask(VEHICLE_ID);

        assertTrue(result.isOk());
        IOTAManagementService.CancelTaskResponse resp = result.unwrap();
        assertNotNull(resp.taskId());

        ArgumentCaptor<Vehicle> captor = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).save(captor.capture());
        assertEquals(UpgradeStage.ROLLED_BACK, captor.getValue().otaUpgradeStatus().get().getStage());
        verify(otaDeliveryPort).cancelDelivery(VEHICLE_ID);
    }

    @Test
    void cancelUpgradeTaskShouldReturnErrWhenVehicleNotFound() {
        when(vehicleRepository.findById("unknown")).thenReturn(Optional.empty());

        Result<IOTAManagementService.CancelTaskResponse, AppError> result =
                service.cancelUpgradeTask(new VehicleId("unknown"));

        assertTrue(result.isErr());
        assertEquals("NotFound", result.unwrapErr().code());
    }

    @Test
    void cancelUpgradeTaskShouldReturnOkWithNothingToCancelWhenNoUpgrade() {
        Vehicle vehicle = createVehicle();
        when(vehicleRepository.findById(VEHICLE_ID.id())).thenReturn(Optional.of(vehicle));

        Result<IOTAManagementService.CancelTaskResponse, AppError> result =
                service.cancelUpgradeTask(VEHICLE_ID);

        assertTrue(result.isOk());
        assertEquals("NOTHING_TO_CANCEL", result.unwrap().status());
        verify(otaDeliveryPort, never()).cancelDelivery(any());
    }

    @Test
    void cancelUpgradeTaskShouldReturnErrWhenAlreadyCompleted() {
        Vehicle vehicle = createVehicleWithStatus(UpgradeStage.COMPLETED);
        when(vehicleRepository.findById(VEHICLE_ID.id())).thenReturn(Optional.of(vehicle));

        Result<IOTAManagementService.CancelTaskResponse, AppError> result =
                service.cancelUpgradeTask(VEHICLE_ID);

        assertTrue(result.isErr());
        assertEquals("UpgradeAlreadyFinished", result.unwrapErr().code());
    }

    @Test
    void cancelUpgradeTaskShouldReturnOkWhenAlreadyRolledBack() {
        Vehicle vehicle = createVehicleWithStatus(UpgradeStage.ROLLED_BACK);
        when(vehicleRepository.findById(VEHICLE_ID.id())).thenReturn(Optional.of(vehicle));

        Result<IOTAManagementService.CancelTaskResponse, AppError> result =
                service.cancelUpgradeTask(VEHICLE_ID);

        assertTrue(result.isOk());
        assertNotNull(result.unwrap().taskId());
    }

    @Test
    void cancelUpgradeTaskShouldReturnOkWhenRollingBack() {
        Vehicle vehicle = createVehicleWithStatus(UpgradeStage.ROLLING_BACK);
        when(vehicleRepository.findById(VEHICLE_ID.id())).thenReturn(Optional.of(vehicle));

        Result<IOTAManagementService.CancelTaskResponse, AppError> result =
                service.cancelUpgradeTask(VEHICLE_ID);

        assertTrue(result.isOk());
        assertNotNull(result.unwrap().taskId());
    }

    @Test
    void cancelUpgradeTaskShouldCancelPendingTask() {
        Vehicle vehicle = createVehicleWithStatus(UpgradeStage.PENDING);
        when(vehicleRepository.findById(VEHICLE_ID.id())).thenReturn(Optional.of(vehicle));

        Result<IOTAManagementService.CancelTaskResponse, AppError> result =
                service.cancelUpgradeTask(VEHICLE_ID);

        assertTrue(result.isOk());
        assertNotNull(result.unwrap().taskId());
        verify(vehicleRepository).save(any(Vehicle.class));
        verify(otaDeliveryPort).cancelDelivery(VEHICLE_ID);
    }
}
