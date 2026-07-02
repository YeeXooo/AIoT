package com.aiot.domain.ota;

import com.aiot.domain.event.CameraOcclusionDetectedEvent;
import com.aiot.domain.event.CameraOcclusionRemovedEvent;
import com.aiot.domain.event.DomainEventPublisher;
import com.aiot.domain.model.Vehicle;
import com.aiot.domain.port.CameraOcclusionDetectionPort;
import com.aiot.domain.repository.VehicleRepository;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.VehicleId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SensorSelfCheckServiceImpl implements SensorSelfCheckService {

    private final DomainEventPublisher eventPublisher;
    private final VehicleRepository vehicleRepository;
    private final CameraOcclusionDetectionPort cameraOcclusionDetectionPort;

    public SensorSelfCheckServiceImpl(
            DomainEventPublisher eventPublisher,
            VehicleRepository vehicleRepository,
            CameraOcclusionDetectionPort cameraOcclusionDetectionPort) {
        this.eventPublisher = eventPublisher;
        this.vehicleRepository = vehicleRepository;
        this.cameraOcclusionDetectionPort = cameraOcclusionDetectionPort;
    }

    @Override
    public Result<SelfCheckResult, AppError> runSelfCheck(VehicleId vehicleId) {
        Optional<Vehicle> vehicleOpt = vehicleRepository.findById(vehicleId.id());
        if (vehicleOpt.isEmpty()) {
            return Result.err(AppError.notFound("Vehicle", vehicleId.id()));
        }
        Vehicle vehicle = vehicleOpt.get();

        Map<String, String> sensorStatuses = new HashMap<>();
        List<String> occludedSensors = new ArrayList<>();

        vehicle.sensorStatusMap().forEach((sensorId, status) -> {
            sensorStatuses.put(sensorId, status.name());
            if ("FAULT".equals(status.name()) || "OFFLINE".equals(status.name())) {
                occludedSensors.add(sensorId);
            }
        });

        return Result.ok(new SelfCheckResult(sensorStatuses, occludedSensors));
    }

    @Override
    public void onOcclusionDetected(String vehicleId, String sensorId, long timestamp) {
        VehicleId vid = new VehicleId(vehicleId);
        CameraOcclusionDetectedEvent event = new CameraOcclusionDetectedEvent(
                vid, sensorId, Instant.ofEpochMilli(timestamp)
        );
        eventPublisher.publish(event);
    }

    @Override
    public void onOcclusionRemoved(String vehicleId, String sensorId, long timestamp) {
        VehicleId vid = new VehicleId(vehicleId);
        CameraOcclusionRemovedEvent event = new CameraOcclusionRemovedEvent(
                vid, sensorId, Instant.ofEpochMilli(timestamp)
        );
        eventPublisher.publish(event);
    }
}
