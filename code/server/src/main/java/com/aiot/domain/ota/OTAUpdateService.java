package com.aiot.domain.ota;

import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.VehicleId;
import com.aiot.domain.event.DomainEventPublisher;
import com.aiot.domain.event.OTAUpgradeCompletedEvent;
import com.aiot.domain.event.OTAUpgradeFailedEvent;
import com.aiot.domain.port.OTADeliveryPort;
import com.aiot.domain.model.Vehicle;
import com.aiot.domain.repository.VehicleRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class OTAUpdateService {

    private final VehicleRepository vehicleRepo;
    private final OTADeliveryPort otaPort;
    private final DomainEventPublisher eventPublisher;

    public OTAUpdateService(VehicleRepository vehicleRepo, OTADeliveryPort otaPort,
                              DomainEventPublisher eventPublisher) {
        this.vehicleRepo = vehicleRepo;
        this.otaPort = otaPort;
        this.eventPublisher = eventPublisher;
    }

    public Result<Void, AppError> initiateUpgrade(VehicleId vehicleId, String targetVersion) {
        Optional<Vehicle> vehicleOpt = vehicleRepo.findById(vehicleId);
        if (vehicleOpt.isEmpty()) return Result.err(AppError.notFound("Vehicle not found"));

        OTADeliveryPort.OTAPackage pkg = new OTADeliveryPort.OTAPackage(
            targetVersion, 1024 * 1024, "sha256:abc123", 4);

        try {
            otaPort.deliverPackage(vehicleId, pkg, Optional.empty());
            eventPublisher.publish(new OTAUpgradeCompletedEvent(
                vehicleId, targetVersion, Instant.now()));
        } catch (Exception e) {
            eventPublisher.publish(new OTAUpgradeFailedEvent(
                vehicleId, targetVersion, e.getMessage(), Instant.now()));
            return Result.err(AppError.internal("OTA delivery failed: " + e.getMessage()));
        }

        return Result.ok(null);
    }
}
