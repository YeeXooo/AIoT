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

import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class OTAUpdateServiceImpl implements OTAUpdateService {

    private final VehicleRepository vehicleRepository;
    private final DomainEventPublisher eventPublisher;
    private final OTADeliveryPort otaDeliveryPort;

    public OTAUpdateServiceImpl(
            VehicleRepository vehicleRepository,
            DomainEventPublisher eventPublisher,
            OTADeliveryPort otaDeliveryPort) {
        this.vehicleRepository = vehicleRepository;
        this.eventPublisher = eventPublisher;
        this.otaDeliveryPort = otaDeliveryPort;
    }

    @Override
    public Result<Void, AppError> initiateUpgrade(VehicleId vehicleId, OTAVersion targetVersion) {
        Optional<Vehicle> vehicleOpt = vehicleRepository.findById(vehicleId.id());
        if (vehicleOpt.isEmpty()) {
            return Result.err(AppError.notFound("Vehicle", vehicleId.id()));
        }
        Vehicle vehicle = vehicleOpt.get();

        if (vehicle.otaUpgradeStatus().isPresent()) {
            OTAUpgradeStatus currentStatus = vehicle.otaUpgradeStatus().get();
            UpgradeStage stage = currentStatus.getStage();
            if (stage != UpgradeStage.COMPLETED && stage != UpgradeStage.ROLLED_BACK) {
                return Result.err(AppError.upgradeInProgress(vehicleId.id()));
            }
        }

        Instant now = Instant.now();
        OTAUpgradeStatus newStatus = OTAUpgradeStatus.init(targetVersion, now);
        vehicle.updateOTAUpgradeStatus(newStatus);
        vehicleRepository.save(vehicle);

        OTADeliveryPort.OTAPackage pkg = new OTADeliveryPort.OTAPackage(
                targetVersion.getVersionNumber(),
                0L,
                targetVersion.getPackageDigest() != null ? targetVersion.getPackageDigest() : "",
                0
        );
        try {
            otaDeliveryPort.deliverPackage(vehicleId, pkg, Optional.empty());
        } catch (Exception e) {
            OTAUpgradeStatus failedStatus = newStatus.transition(UpgradeStage.ROLLED_BACK, 0, Instant.now());
            vehicle.updateOTAUpgradeStatus(failedStatus);
            vehicleRepository.save(vehicle);

            eventPublisher.publish(new OTAUpgradeRolledBackEvent(
                    vehicleId,
                    targetVersion.getVersionNumber(),
                    "INITIATING",
                    "OTA delivery failed: " + e.getMessage(),
                    now
            ));
            return Result.err(AppError.invalidState("OTA delivery failed: " + e.getMessage()));
        }

        eventPublisher.publish(new OTAUpgradeStartedEvent(
                vehicleId,
                targetVersion.getVersionNumber(),
                now
        ));

        return Result.ok(null);
    }

    @Override
    public Result<UpgradeStage, AppError> handleTransferProgress(
            VehicleId vehicleId, long transferredBytes, long totalBytes, String status) {
        Optional<Vehicle> vehicleOpt = vehicleRepository.findById(vehicleId.id());
        if (vehicleOpt.isEmpty()) {
            return Result.err(AppError.notFound("Vehicle", vehicleId.id()));
        }
        Vehicle vehicle = vehicleOpt.get();

        if (vehicle.otaUpgradeStatus().isEmpty()) {
            return Result.err(AppError.invalidState("No upgrade in progress for vehicle " + vehicleId.id()));
        }

        OTAUpgradeStatus currentStatus = vehicle.otaUpgradeStatus().get();
        Instant now = Instant.now();

        if ("COMPLETED".equals(status) && transferredBytes >= totalBytes) {
            OTAUpgradeStatus nextStatus = currentStatus.transition(UpgradeStage.VERIFYING, transferredBytes, now);
            vehicle.updateOTAUpgradeStatus(nextStatus);
            vehicleRepository.save(vehicle);
            return Result.ok(UpgradeStage.VERIFYING);
        }

        if ("FAILED".equals(status)) {
            OTAUpgradeStatus nextStatus = currentStatus.transition(UpgradeStage.ROLLED_BACK, transferredBytes, now);
            vehicle.updateOTAUpgradeStatus(nextStatus);
            vehicleRepository.save(vehicle);

            eventPublisher.publish(new OTAUpgradeFailedEvent(
                    vehicleId,
                    currentStatus.getTargetVersion() != null
                            ? currentStatus.getTargetVersion().getVersionNumber() : "unknown",
                    "TRANSMITTING",
                    "Transfer failed",
                    now
            ));
            eventPublisher.publish(new OTAUpgradeRolledBackEvent(
                    vehicleId,
                    currentStatus.getTargetVersion() != null
                            ? currentStatus.getTargetVersion().getVersionNumber() : "unknown",
                    "TRANSMITTING",
                    "Transfer failed",
                    now
            ));
            return Result.ok(UpgradeStage.ROLLED_BACK);
        }

        OTAUpgradeStatus nextStatus = currentStatus.transition(UpgradeStage.TRANSMITTING, transferredBytes, now);
        vehicle.updateOTAUpgradeStatus(nextStatus);
        vehicleRepository.save(vehicle);
        return Result.ok(UpgradeStage.TRANSMITTING);
    }

    @Override
    public Result<UpgradeStage, AppError> handleVerificationResult(VehicleId vehicleId, boolean checksumValid) {
        Optional<Vehicle> vehicleOpt = vehicleRepository.findById(vehicleId.id());
        if (vehicleOpt.isEmpty()) {
            return Result.err(AppError.notFound("Vehicle", vehicleId.id()));
        }
        Vehicle vehicle = vehicleOpt.get();

        if (vehicle.otaUpgradeStatus().isEmpty()) {
            return Result.err(AppError.invalidState("No upgrade in progress for vehicle " + vehicleId.id()));
        }

        OTAUpgradeStatus currentStatus = vehicle.otaUpgradeStatus().get();
        Instant now = Instant.now();

        if (!checksumValid) {
            OTAUpgradeStatus nextStatus = currentStatus.transition(UpgradeStage.ROLLED_BACK, currentStatus.getOffset(), now);
            vehicle.updateOTAUpgradeStatus(nextStatus);
            vehicleRepository.save(vehicle);

            eventPublisher.publish(new OTAUpgradeFailedEvent(
                    vehicleId,
                    currentStatus.getTargetVersion() != null
                            ? currentStatus.getTargetVersion().getVersionNumber() : "unknown",
                    "VERIFYING",
                    "Checksum mismatch",
                    now
            ));
            eventPublisher.publish(new OTAUpgradeRolledBackEvent(
                    vehicleId,
                    currentStatus.getTargetVersion() != null
                            ? currentStatus.getTargetVersion().getVersionNumber() : "unknown",
                    "VERIFYING",
                    "Checksum mismatch",
                    now
            ));
            return Result.ok(UpgradeStage.ROLLED_BACK);
        }

        OTAUpgradeStatus nextStatus = currentStatus.transition(UpgradeStage.READY, currentStatus.getOffset(), now);
        vehicle.updateOTAUpgradeStatus(nextStatus);
        vehicleRepository.save(vehicle);

        OTAUpgradeStatus upgradeStatus = currentStatus.transition(UpgradeStage.UPGRADING, currentStatus.getOffset(), now);
        vehicle.updateOTAUpgradeStatus(upgradeStatus);
        vehicleRepository.save(vehicle);
        return Result.ok(UpgradeStage.UPGRADING);
    }

    @Override
    public Result<UpgradeStage, AppError> handleFirmwareFlashResult(VehicleId vehicleId, boolean flashSuccess) {
        Optional<Vehicle> vehicleOpt = vehicleRepository.findById(vehicleId.id());
        if (vehicleOpt.isEmpty()) {
            return Result.err(AppError.notFound("Vehicle", vehicleId.id()));
        }
        Vehicle vehicle = vehicleOpt.get();

        if (vehicle.otaUpgradeStatus().isEmpty()) {
            return Result.err(AppError.invalidState("No upgrade in progress for vehicle " + vehicleId.id()));
        }

        OTAUpgradeStatus currentStatus = vehicle.otaUpgradeStatus().get();
        Instant now = Instant.now();

        if (flashSuccess) {
            OTAUpgradeStatus nextStatus = currentStatus.transition(UpgradeStage.COMPLETED, currentStatus.getOffset(), now);
            vehicle.updateOTAUpgradeStatus(nextStatus);

            String oldVersion = vehicle.firmwareVersion() != null
                    ? vehicle.firmwareVersion().getVersionNumber() : "unknown";
            String targetVersion = currentStatus.getTargetVersion() != null
                    ? currentStatus.getTargetVersion().getVersionNumber() : "unknown";
            if (currentStatus.getTargetVersion() != null) {
                vehicle.updateFirmwareVersion(currentStatus.getTargetVersion());
            }
            vehicleRepository.save(vehicle);

            eventPublisher.publish(new OTAUpgradeCompletedEvent(
                    vehicleId,
                    oldVersion,
                    targetVersion,
                    0L,
                    now
            ));

            return Result.ok(UpgradeStage.COMPLETED);
        }

        OTAUpgradeStatus nextStatus = currentStatus.transition(UpgradeStage.ROLLED_BACK, currentStatus.getOffset(), now);
        vehicle.updateOTAUpgradeStatus(nextStatus);
        vehicleRepository.save(vehicle);

        eventPublisher.publish(new OTAUpgradeFailedEvent(
                vehicleId,
                currentStatus.getTargetVersion() != null
                        ? currentStatus.getTargetVersion().getVersionNumber() : "unknown",
                "UPGRADING",
                "Firmware flash failed",
                now
        ));
        eventPublisher.publish(new OTAUpgradeRolledBackEvent(
                vehicleId,
                currentStatus.getTargetVersion() != null
                        ? currentStatus.getTargetVersion().getVersionNumber() : "unknown",
                "UPGRADING",
                "Firmware flash failed",
                now
        ));

        return Result.ok(UpgradeStage.ROLLED_BACK);
    }
}
