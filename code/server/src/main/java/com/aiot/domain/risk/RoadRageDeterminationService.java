package com.aiot.domain.risk;

import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.TripId;
import com.aiot.domain.event.DomainEventPublisher;
import com.aiot.domain.event.RiskDeterminedEvent;
import com.aiot.domain.event.AlertType;
import com.aiot.domain.event.RiskLevel;
import com.aiot.domain.model.Trip;
import com.aiot.domain.repository.TripRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class RoadRageDeterminationService {

    private final TripRepository tripRepo;
    private final DomainEventPublisher eventPublisher;

    public RoadRageDeterminationService(TripRepository tripRepo,
                                         DomainEventPublisher eventPublisher) {
        this.tripRepo = tripRepo;
        this.eventPublisher = eventPublisher;
    }

    public Result<RiskDeterminedEvent, AppError> determineRoadRage(TripId tripId,
            double soundPressureDb, String detectedKeywords) {
        Optional<Trip> tripOpt = tripRepo.findById(tripId);
        if (tripOpt.isEmpty()) return Result.err(AppError.notFound("Trip not found: " + tripId));

        // 第一期打桩：声压 > 85dB 或检测到谩骂关键词 → DANGER
        if (soundPressureDb > 85.0 || detectedKeywords != null && !detectedKeywords.isEmpty()) {
            RiskDeterminedEvent event = new RiskDeterminedEvent(
                tripId, RiskLevel.DANGER, AlertType.ROAD_RAGE,
                Instant.now(), "Sound pressure: " + soundPressureDb + "dB, keywords: " + detectedKeywords);
            eventPublisher.publish(event);
            return Result.ok(event);
        }
        return Result.ok(null);
    }
}
