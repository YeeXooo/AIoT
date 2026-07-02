package com.aiot.domain.risk;

import com.aiot.domain.event.AlertType;
import com.aiot.domain.event.DomainEventPublisher;
import com.aiot.domain.event.RiskDeterminedEvent;
import com.aiot.domain.event.RiskLevel;
import com.aiot.domain.event.RiskResolvedEvent;
import com.aiot.domain.model.SensorReading;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.TripId;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RiskDeterminationServiceImpl implements RiskDeterminationService {

    private final FatigueDeterminationService fatigueService;
    private final DistractionDetectionService distractionService;
    private final RoadRageDeterminationService roadRageService;
    private final DomainEventPublisher eventPublisher;

    public RiskDeterminationServiceImpl(FatigueDeterminationService fatigueService,
                                        DistractionDetectionService distractionService,
                                        RoadRageDeterminationService roadRageService,
                                        DomainEventPublisher eventPublisher) {
        this.fatigueService = fatigueService;
        this.distractionService = distractionService;
        this.roadRageService = roadRageService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Result<DeterminationResult, AppError> executeStreamFusion(
            List<SensorReading> readings, ActiveRiskSet currentRiskSet) {

        List<RiskDeterminedEvent> determinedEvents = new ArrayList<>();
        List<RiskResolvedEvent> resolvedEvents = new ArrayList<>();
        ActiveRiskSet updatedSet = currentRiskSet;
        TripId tripId = new TripId(currentRiskSet.tripId());

        Set<AlertType> detectedTypes = new HashSet<>();

        for (SensorReading reading : readings) {
            String channelType = reading.getChannelType();
            Instant detectionTime = reading.getTimestamp();

            switch (channelType) {
                case "FACE_CAMERA" -> {
                    detectedTypes.add(AlertType.FATIGUE);
                    processFatigueReading(reading, tripId, detectionTime,
                            currentRiskSet, determinedEvents, resolvedEvents);
                }
                case "GAZE_TRACKER" -> {
                    detectedTypes.add(AlertType.DISTRACTION);
                    processDistractionReading(reading, tripId, detectionTime,
                            currentRiskSet, determinedEvents, resolvedEvents);
                }
                case "AUDIO_SENSOR" -> {
                    detectedTypes.add(AlertType.ROAD_RAGE);
                    processRoadRageReading(reading, tripId, detectionTime,
                            currentRiskSet, determinedEvents, resolvedEvents);
                }
                default -> {
                }
            }
        }

        for (AlertType activeType : currentRiskSet.activeRisks().keySet()) {
            if (!detectedTypes.contains(activeType)) {
                RiskResolvedEvent resolved = new RiskResolvedEvent(
                        tripId, activeType, Instant.now());
                resolvedEvents.add(resolved);
                updatedSet = updatedSet.remove(activeType);
            }
        }

        determinedEvents.forEach(eventPublisher::publish);
        resolvedEvents.forEach(eventPublisher::publish);

        return Result.ok(new DeterminationResult(updatedSet, determinedEvents, resolvedEvents));
    }

    private void processFatigueReading(SensorReading reading, TripId tripId, Instant detectionTime,
                                       ActiveRiskSet currentRiskSet,
                                       List<RiskDeterminedEvent> determinedEvents,
                                       List<RiskResolvedEvent> resolvedEvents) {
        fatigueService.determineFatigue(reading).ifOk(result -> {
            if (!"NORMAL".equals(result.riskLevel())) {
                if (!currentRiskSet.isActive(AlertType.FATIGUE)) {
                    RiskLevel level = parseRiskLevel(result.riskLevel());
                    RiskDeterminedEvent event = new RiskDeterminedEvent(
                            tripId, level, AlertType.FATIGUE, detectionTime, result.summary());
                    determinedEvents.add(event);
                }
            } else {
                if (currentRiskSet.isActive(AlertType.FATIGUE)) {
                    RiskResolvedEvent event = new RiskResolvedEvent(
                            tripId, AlertType.FATIGUE, Instant.now());
                    resolvedEvents.add(event);
                }
            }
        });
    }

    private void processDistractionReading(SensorReading reading, TripId tripId, Instant detectionTime,
                                           ActiveRiskSet currentRiskSet,
                                           List<RiskDeterminedEvent> determinedEvents,
                                           List<RiskResolvedEvent> resolvedEvents) {
        distractionService.detectDistraction(reading).ifOk(result -> {
            if (!"NORMAL".equals(result.riskLevel())) {
                if (!currentRiskSet.isActive(AlertType.DISTRACTION)) {
                    RiskLevel level = parseRiskLevel(result.riskLevel());
                    RiskDeterminedEvent event = new RiskDeterminedEvent(
                            tripId, level, AlertType.DISTRACTION, detectionTime, result.summary());
                    determinedEvents.add(event);
                }
            } else {
                if (currentRiskSet.isActive(AlertType.DISTRACTION)) {
                    RiskResolvedEvent event = new RiskResolvedEvent(
                            tripId, AlertType.DISTRACTION, Instant.now());
                    resolvedEvents.add(event);
                }
            }
        });
    }

    private void processRoadRageReading(SensorReading reading, TripId tripId, Instant detectionTime,
                                        ActiveRiskSet currentRiskSet,
                                        List<RiskDeterminedEvent> determinedEvents,
                                        List<RiskResolvedEvent> resolvedEvents) {
        roadRageService.determineRoadRage(reading).ifOk(result -> {
            if (!"NORMAL".equals(result.riskLevel())) {
                if (!currentRiskSet.isActive(AlertType.ROAD_RAGE)) {
                    RiskLevel level = parseRiskLevel(result.riskLevel());
                    RiskDeterminedEvent event = new RiskDeterminedEvent(
                            tripId, level, AlertType.ROAD_RAGE, detectionTime, result.summary());
                    determinedEvents.add(event);
                }
            } else {
                if (currentRiskSet.isActive(AlertType.ROAD_RAGE)) {
                    RiskResolvedEvent event = new RiskResolvedEvent(
                            tripId, AlertType.ROAD_RAGE, Instant.now());
                    resolvedEvents.add(event);
                }
            }
        });
    }

    private RiskLevel parseRiskLevel(String riskLevel) {
        try {
            return RiskLevel.valueOf(riskLevel);
        } catch (IllegalArgumentException e) {
            return RiskLevel.L2_WARNING;
        }
    }
}
