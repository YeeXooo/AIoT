package com.aiot.application;

import com.aiot.domain.event.AlertTriggeredEvent;
import com.aiot.domain.event.BehaviorCountersUpdated;
import com.aiot.domain.event.DomainEventPublisher;
import com.aiot.domain.event.PhysiologicalDataUpdated;
import com.aiot.domain.event.RiskDeterminedEvent;
import com.aiot.domain.event.RiskLevel;
import com.aiot.domain.event.SafetyAlertDetectedEvent;
import com.aiot.domain.event.SensorDataCollected;
import com.aiot.domain.event.VehicleStateUpdated;
import com.aiot.domain.model.Trip;
import com.aiot.domain.model.Vehicle;
import com.aiot.domain.repository.TripRepository;
import com.aiot.domain.repository.VehicleRepository;
import com.aiot.interfaces.websocket.FleetWebSocketHandler;
import com.aiot.interfaces.websocket.GuardianshipWebSocketHandler;
import com.aiot.interfaces.websocket.WebSocketPayloads;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class IotdaEventBridgeService {

    private static final Logger log = LoggerFactory.getLogger(IotdaEventBridgeService.class);

    private final DomainEventPublisher eventPublisher;
    private final TripRepository tripRepository;
    private final VehicleRepository vehicleRepository;
    private final GuardianshipWebSocketHandler guardianshipWs;
    private final FleetWebSocketHandler fleetWs;

    public IotdaEventBridgeService(DomainEventPublisher eventPublisher,
                                    TripRepository tripRepository,
                                    VehicleRepository vehicleRepository,
                                    GuardianshipWebSocketHandler guardianshipWs,
                                    FleetWebSocketHandler fleetWs) {
        this.eventPublisher = eventPublisher;
        this.tripRepository = tripRepository;
        this.vehicleRepository = vehicleRepository;
        this.guardianshipWs = guardianshipWs;
        this.fleetWs = fleetWs;
    }

    @EventListener
    public void onSafetyAlertDetected(SafetyAlertDetectedEvent event) {
        log.info("桥接 SafetyAlertDetected → RiskDetermined: tripId={}, type={}, level={}",
                event.tripId().id(), event.alertType(), event.riskLevel());

        RiskDeterminedEvent riskEvent = new RiskDeterminedEvent(
                event.tripId(),
                event.riskLevel(),
                event.alertType(),
                event.alertTime(),
                event.alertMessage());

        eventPublisher.publish(riskEvent);
    }

    @EventListener
    public void onAlertTriggered(AlertTriggeredEvent event) {
        log.info("AlertTriggered → WebSocket 推送: tripId={}, type={}, level={}",
                event.tripId().id(), event.alertType(), event.riskLevel());

        Optional<Trip> tripOpt = tripRepository.findById(event.tripId().id());
        if (tripOpt.isEmpty()) {
            log.warn("无法找到行程，跳过 WebSocket 推送: tripId={}", event.tripId().id());
            return;
        }

        Trip trip = tripOpt.get();
        String driverId = trip.driverId().id();
        String vehicleId = trip.vehicleId().id();

        WebSocketPayloads.AlertTriggered wsAlert = WebSocketPayloads.AlertTriggered.of(
                event.alertId().id(),
                event.alertType().name(),
                event.riskLevel().name(),
                event.alertTime(),
                event.tripId().id());

        guardianshipWs.broadcastAlertToSubscribers(driverId, wsAlert);

        if (event.riskLevel() == RiskLevel.L3_CRITICAL) {
            String fleetId = vehicleRepository.findById(vehicleId)
                    .flatMap(Vehicle::fleetId)
                    .orElse("unknown");
            fleetWs.broadcastL3Alert(fleetId, driverId, vehicleId,
                    event.alertType().name(), event.alertTime());
        }

        WebSocketPayloads.DriverStatusSnapshot statusSnapshot =
                WebSocketPayloads.DriverStatusSnapshot.create(driverId, vehicleId,
                        event.riskLevel().name());
        guardianshipWs.broadcastDriverStatus(driverId, statusSnapshot);
    }

    @EventListener
    public void onSensorDataCollected(SensorDataCollected event) {
        log.debug("SensorDataCollected: tripId={}, readings={}", event.tripId().id(), event.readings().size());
    }

    @EventListener
    public void onPhysiologicalDataUpdated(PhysiologicalDataUpdated event) {
        log.debug("PhysiologicalDataUpdated: tripId={}, hr={}, spo2={}",
                event.tripId().id(),
                event.snapshot().heartRate(),
                event.snapshot().bloodOxygen());
    }

    @EventListener
    public void onVehicleStateUpdated(VehicleStateUpdated event) {
        log.debug("VehicleStateUpdated: tripId={}, lat={}, lon={}",
                event.tripId().id(),
                event.state().latitude(),
                event.state().longitude());
    }

    @EventListener
    public void onBehaviorCountersUpdated(BehaviorCountersUpdated event) {
        log.debug("BehaviorCountersUpdated: tripId={}, brake={}, accel={}, sharpTurn={}",
                event.tripId().id(),
                event.counters().getSuddenBrakingCount(),
                event.counters().getSuddenAccelerationCount(),
                event.counters().getSharpTurnCount());
    }
}
