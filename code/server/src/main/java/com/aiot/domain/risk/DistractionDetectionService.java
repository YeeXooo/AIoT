package com.aiot.domain.risk;

import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.TripId;
import com.aiot.domain.event.DomainEventPublisher;
import com.aiot.domain.event.RiskDeterminedEvent;
import com.aiot.domain.event.AlertType;
import com.aiot.domain.event.RiskLevel;
import com.aiot.domain.port.CameraOcclusionDetectionPort;
import com.aiot.domain.model.Trip;
import com.aiot.domain.repository.TripRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class DistractionDetectionService {

    private final TripRepository tripRepo;
    private final CameraOcclusionDetectionPort cameraPort;
    private final DomainEventPublisher eventPublisher;

    public DistractionDetectionService(TripRepository tripRepo,
                                        CameraOcclusionDetectionPort cameraPort,
                                        DomainEventPublisher eventPublisher) {
        this.tripRepo = tripRepo;
        this.cameraPort = cameraPort;
        this.eventPublisher = eventPublisher;
    }

    public Result<RiskDeterminedEvent, AppError> detectDistraction(TripId tripId) {
        Optional<Trip> tripOpt = tripRepo.findById(tripId);
        if (tripOpt.isEmpty()) return Result.err(AppError.notFound("Trip not found: " + tripId));

        // 第一期打桩：摄像头遮挡 → 判定分心 DANGER
        RiskDeterminedEvent event = new RiskDeterminedEvent(
            tripId, RiskLevel.DANGER, AlertType.DISTRACTION,
            Instant.now(), "Camera occlusion detected — possible distraction");
        eventPublisher.publish(event);
        return Result.ok(event);
    }
}
