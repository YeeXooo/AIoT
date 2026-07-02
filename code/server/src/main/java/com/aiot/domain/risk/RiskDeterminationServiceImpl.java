package com.aiot.domain.risk;

import com.aiot.domain.event.AlertType;
import com.aiot.domain.event.DomainEventPublisher;
import com.aiot.domain.event.RiskDeterminedEvent;
import com.aiot.domain.event.RiskResolvedEvent;
import com.aiot.domain.model.SensorReading;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.TripId;

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
        SensorReading voiceReading = null;
        SensorReading physioReading = null;
        Instant roadRageTime = null;

        for (SensorReading reading : readings) {
            SensorReading.SensorType sensorType = reading.sensorType();
            Instant detectionTime = reading.timestamp();

            switch (sensorType) {
                case DMS_CAMERA -> {
                    detectedTypes.add(AlertType.FATIGUE);
                    detectedTypes.add(AlertType.DISTRACTION);
                    updatedSet = processFatigueReading(reading, tripId, detectionTime,
                            updatedSet, determinedEvents, resolvedEvents);
                    updatedSet = processDistractionReading(reading, tripId, detectionTime,
                            updatedSet, determinedEvents, resolvedEvents);
                }
                case MICROPHONE -> {
                    voiceReading = reading;
                    roadRageTime = detectionTime;
                }
                case PHYSIOLOGICAL_MONITOR -> {
                    physioReading = reading;
                }
                default -> {
                }
            }
        }

        if (voiceReading != null && physioReading != null) {
            detectedTypes.add(AlertType.ROAD_RAGE);
            updatedSet = processRoadRageReading(voiceReading, physioReading, tripId, roadRageTime,
                    updatedSet, determinedEvents, resolvedEvents);
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

    private ActiveRiskSet processFatigueReading(SensorReading reading, TripId tripId, Instant detectionTime,
                                                ActiveRiskSet updatedSet,
                                                List<RiskDeterminedEvent> determinedEvents,
                                                List<RiskResolvedEvent> resolvedEvents) {
        var result = fatigueService.determineFatigue(reading);
        if (result.isOk()) {
            var detResult = result.unwrap();
            if (detResult.riskLevel() != null) {
                if (!updatedSet.isActive(AlertType.FATIGUE)) {
                    RiskDeterminedEvent event = new RiskDeterminedEvent(
                            tripId, detResult.riskLevel(), AlertType.FATIGUE, detectionTime, detResult.summary());
                    determinedEvents.add(event);
                    return updatedSet.add(AlertType.FATIGUE, detResult.riskLevel(), detectionTime, detResult.summary());
                }
            } else {
                if (updatedSet.isActive(AlertType.FATIGUE)) {
                    RiskResolvedEvent event = new RiskResolvedEvent(
                            tripId, AlertType.FATIGUE, Instant.now());
                    resolvedEvents.add(event);
                    return updatedSet.remove(AlertType.FATIGUE);
                }
            }
        }
        return updatedSet;
    }

    private ActiveRiskSet processDistractionReading(SensorReading reading, TripId tripId, Instant detectionTime,
                                                    ActiveRiskSet updatedSet,
                                                    List<RiskDeterminedEvent> determinedEvents,
                                                    List<RiskResolvedEvent> resolvedEvents) {
        var result = distractionService.detectDistraction(reading);
        if (result.isOk()) {
            var detResult = result.unwrap();
            if (detResult.riskLevel() != null) {
                if (!updatedSet.isActive(AlertType.DISTRACTION)) {
                    RiskDeterminedEvent event = new RiskDeterminedEvent(
                            tripId, detResult.riskLevel(), AlertType.DISTRACTION, detectionTime, detResult.summary());
                    determinedEvents.add(event);
                    return updatedSet.add(AlertType.DISTRACTION, detResult.riskLevel(), detectionTime, detResult.summary());
                }
            } else {
                if (updatedSet.isActive(AlertType.DISTRACTION)) {
                    RiskResolvedEvent event = new RiskResolvedEvent(
                            tripId, AlertType.DISTRACTION, Instant.now());
                    resolvedEvents.add(event);
                    return updatedSet.remove(AlertType.DISTRACTION);
                }
            }
        }
        return updatedSet;
    }

    private ActiveRiskSet processRoadRageReading(SensorReading voiceReading, SensorReading physioReading,
                                                TripId tripId, Instant detectionTime,
                                                ActiveRiskSet updatedSet,
                                                List<RiskDeterminedEvent> determinedEvents,
                                                List<RiskResolvedEvent> resolvedEvents) {
        var result = roadRageService.determineRoadRage(voiceReading, physioReading);
        if (result.isOk()) {
            var detResult = result.unwrap();
            if (detResult.riskLevel() != null) {
                if (!updatedSet.isActive(AlertType.ROAD_RAGE)) {
                    RiskDeterminedEvent event = new RiskDeterminedEvent(
                            tripId, detResult.riskLevel(), AlertType.ROAD_RAGE, detectionTime, detResult.summary());
                    determinedEvents.add(event);
                    return updatedSet.add(AlertType.ROAD_RAGE, detResult.riskLevel(), detectionTime, detResult.summary());
                }
            } else {
                if (updatedSet.isActive(AlertType.ROAD_RAGE)) {
                    RiskResolvedEvent event = new RiskResolvedEvent(
                            tripId, AlertType.ROAD_RAGE, Instant.now());
                    resolvedEvents.add(event);
                    return updatedSet.remove(AlertType.ROAD_RAGE);
                }
            }
        }
        return updatedSet;
    }
}
