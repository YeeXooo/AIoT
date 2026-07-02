package com.aiot.application.ota;

import com.aiot.domain.model.OTAUpgradeStatus;
import com.aiot.domain.model.OTAVersion;
import com.aiot.domain.model.UpgradeStage;
import com.aiot.domain.model.Vehicle;
import com.aiot.domain.ota.OTAUpdateService;
import com.aiot.domain.port.NotificationPort;
import com.aiot.domain.port.OTADeliveryPort;
import com.aiot.domain.repository.VehicleRepository;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.UpgradeTaskId;
import com.aiot.domain.shared.VehicleId;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class OTAManagementServiceImpl implements IOTAManagementService {

    private final OTAUpdateService otaUpdateService;
    private final VehicleRepository vehicleRepository;
    private final OTADeliveryPort otaDeliveryPort;
    private final NotificationPort notificationPort;

    public OTAManagementServiceImpl(
            OTAUpdateService otaUpdateService,
            VehicleRepository vehicleRepository,
            OTADeliveryPort otaDeliveryPort,
            NotificationPort notificationPort) {
        this.otaUpdateService = otaUpdateService;
        this.vehicleRepository = vehicleRepository;
        this.otaDeliveryPort = otaDeliveryPort;
        this.notificationPort = notificationPort;
    }

    @Override
    public Result<CreateTaskResponse, AppError> createUpgradeTask(
            VehicleId vehicleId, OTAVersion targetVersion, String idempotencyKey) {
        Optional<Vehicle> vehicleOpt = vehicleRepository.findById(vehicleId.id());
        if (vehicleOpt.isEmpty()) {
            return Result.err(AppError.notFound("Vehicle", vehicleId.id()));
        }
        Vehicle vehicle = vehicleOpt.get();

        if (vehicle.otaUpgradeStatus().isPresent()) {
            OTAUpgradeStatus status = vehicle.otaUpgradeStatus().get();
            UpgradeStage stage = status.getStage();
            if (stage != UpgradeStage.COMPLETED && stage != UpgradeStage.ROLLED_BACK) {
                return Result.err(AppError.upgradeInProgress(vehicleId.id()));
            }
        }

        Result<Void, AppError> result = otaUpdateService.initiateUpgrade(vehicleId, targetVersion);
        if (result.isErr()) {
            return Result.err(result.unwrapErr());
        }

        UpgradeTaskId taskId = UpgradeTaskId.generate();
        return Result.ok(new CreateTaskResponse(taskId.id(), UpgradeStage.PENDING.name()));
    }

    @Override
    public Result<UpgradeProgressResponse, AppError> queryUpgradeProgress(VehicleId vehicleId) {
        Optional<Vehicle> vehicleOpt = vehicleRepository.findById(vehicleId.id());
        if (vehicleOpt.isEmpty()) {
            return Result.err(AppError.notFound("Vehicle", vehicleId.id()));
        }
        Vehicle vehicle = vehicleOpt.get();

        if (vehicle.otaUpgradeStatus().isEmpty()) {
            return Result.ok(new UpgradeProgressResponse(
                    UpgradeStage.COMPLETED.name(), 0, 0,
                    vehicle.firmwareVersion() != null
                            ? vehicle.firmwareVersion().getVersionNumber() : "unknown",
                    vehicle.firmwareVersion() != null
                            ? vehicle.firmwareVersion().getVersionNumber() : "unknown"));
        }

        OTAUpgradeStatus status = vehicle.otaUpgradeStatus().get();
        return Result.ok(new UpgradeProgressResponse(
                status.getStage().name(),
                status.getOffset(),
                0L,
                vehicle.firmwareVersion() != null
                        ? vehicle.firmwareVersion().getVersionNumber() : "unknown",
                status.getTargetVersion() != null
                        ? status.getTargetVersion().getVersionNumber() : "unknown"));
    }

    @Override
    public Result<RollbackResponse, AppError> triggerRollback(VehicleId vehicleId) {
        Optional<Vehicle> vehicleOpt = vehicleRepository.findById(vehicleId.id());
        if (vehicleOpt.isEmpty()) {
            return Result.err(AppError.notFound("Vehicle", vehicleId.id()));
        }
        Vehicle vehicle = vehicleOpt.get();

        if (vehicle.otaUpgradeStatus().isEmpty()) {
            return Result.err(AppError.invalidState("No upgrade in progress for vehicle " + vehicleId.id()));
        }

        OTAUpgradeStatus currentStatus = vehicle.otaUpgradeStatus().get();
        String previousStage = currentStatus.getStage().name();
        String rolledBackVersion = vehicle.firmwareVersion() != null
                ? vehicle.firmwareVersion().getVersionNumber() : "unknown";

        OTAUpgradeStatus rolledBackStatus = currentStatus.transition(
                UpgradeStage.ROLLED_BACK, currentStatus.getOffset(), Instant.now());
        vehicle.updateOTAUpgradeStatus(rolledBackStatus);
        vehicleRepository.save(vehicle);

        otaDeliveryPort.cancelDelivery(vehicleId);

        UpgradeTaskId taskId = UpgradeTaskId.generate();
        return Result.ok(new RollbackResponse(taskId.id(), previousStage, rolledBackVersion));
    }

    @Override
    public Result<UpgradeHistoryResponse, AppError> queryUpgradeHistory(
            VehicleId vehicleId, int page, int size) {
        Optional<Vehicle> vehicleOpt = vehicleRepository.findById(vehicleId.id());
        if (vehicleOpt.isEmpty()) {
            return Result.err(AppError.notFound("Vehicle", vehicleId.id()));
        }
        Vehicle vehicle = vehicleOpt.get();

        List<UpgradeHistoryItem> items = new ArrayList<>();
        if (vehicle.otaUpgradeStatus().isPresent()) {
            OTAUpgradeStatus status = vehicle.otaUpgradeStatus().get();
            String taskId = UpgradeTaskId.generate().id();
            String targetVersion = status.getTargetVersion() != null
                    ? status.getTargetVersion().getVersionNumber() : "unknown";
            long createdAt = status.getStageTimestamp() != null
                    ? status.getStageTimestamp().toEpochMilli() : 0L;
            items.add(new UpgradeHistoryItem(
                    taskId, vehicleId.id(), targetVersion,
                    status.getStage().name(), createdAt));
        }

        long totalCount = items.size();
        int fromIndex = (page - 1) * size;
        if (fromIndex >= items.size()) {
            return Result.ok(new UpgradeHistoryResponse(Collections.emptyList(), totalCount));
        }
        int toIndex = Math.min(fromIndex + size, items.size());
        return Result.ok(new UpgradeHistoryResponse(
                items.subList(fromIndex, toIndex), totalCount));
    }

    @Override
    public Result<CancelTaskResponse, AppError> cancelUpgradeTask(VehicleId vehicleId) {
        Optional<Vehicle> vehicleOpt = vehicleRepository.findById(vehicleId.id());
        if (vehicleOpt.isEmpty()) {
            return Result.err(AppError.notFound("Vehicle", vehicleId.id()));
        }
        Vehicle vehicle = vehicleOpt.get();

        if (vehicle.otaUpgradeStatus().isEmpty()) {
            return Result.ok(new CancelTaskResponse("", "NOTHING_TO_CANCEL"));
        }

        OTAUpgradeStatus currentStatus = vehicle.otaUpgradeStatus().get();
        UpgradeStage stage = currentStatus.getStage();

        if (stage == UpgradeStage.COMPLETED) {
            return Result.err(AppError.upgradeAlreadyFinished(
                    vehicleId.id(), stage.name()));
        }

        if (stage == UpgradeStage.ROLLED_BACK || stage == UpgradeStage.ROLLING_BACK) {
            return Result.ok(new CancelTaskResponse(
                    UpgradeTaskId.generate().id(), stage.name()));
        }

        OTAUpgradeStatus cancelledStatus = currentStatus.transition(
                UpgradeStage.ROLLED_BACK, currentStatus.getOffset(), Instant.now());
        vehicle.updateOTAUpgradeStatus(cancelledStatus);
        vehicleRepository.save(vehicle);

        otaDeliveryPort.cancelDelivery(vehicleId);

        UpgradeTaskId taskId = UpgradeTaskId.generate();
        return Result.ok(new CancelTaskResponse(taskId.id(), stage.name()));
    }
}
