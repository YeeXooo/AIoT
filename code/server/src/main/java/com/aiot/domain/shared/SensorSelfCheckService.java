package com.aiot.domain.shared;

import com.aiot.domain.shared.VehicleId;
import com.aiot.domain.model.Vehicle;
import com.aiot.domain.repository.VehicleRepository;
import com.aiot.domain.event.DomainEventPublisher;
import com.aiot.domain.event.SensorFailureEvent;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class SensorSelfCheckService {

    private final VehicleRepository vehicleRepo;
    private final DomainEventPublisher eventPublisher;

    public SensorSelfCheckService(VehicleRepository vehicleRepo,
                                   DomainEventPublisher eventPublisher) {
        this.vehicleRepo = vehicleRepo;
        this.eventPublisher = eventPublisher;
    }

    public void checkSensors(VehicleId vehicleId) {
        Optional<Vehicle> vehicleOpt = vehicleRepo.findById(vehicleId);
        if (vehicleOpt.isPresent() && "FAULT".equals(vehicleOpt.get().getSensorStatus())) {
            SensorFailureEvent event = new SensorFailureEvent(
                vehicleId, "Sensor self-check failed: FAULT status", Instant.now());
            eventPublisher.publish(event);
        }
    }
}
