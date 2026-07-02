package com.aiot.domain.risk;

import com.aiot.domain.event.DomainEventPublisher;
import com.aiot.domain.event.LifeDetectedEvent;
import com.aiot.domain.model.DetectionWindow;
import com.aiot.domain.model.SensorReading;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.VehicleId;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class LifeDetectionServiceImpl implements LifeDetectionService {

    private static final double MOTION_THRESHOLD = 0.15;
    private static final int MIN_MICRO_MOVEMENTS = 3;

    private final DomainEventPublisher eventPublisher;

    public LifeDetectionServiceImpl(DomainEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Result<DetectionResult, AppError> evaluateLifeDetection(SensorReading radarSignal, DetectionWindow window) {
        if (radarSignal == null) {
            return Result.err(AppError.validationFailed("radarSignal must not be null"));
        }
        if (window == null) {
            return Result.err(AppError.validationFailed("window must not be null"));
        }

        if (window.isExpired()) {
            return Result.ok(new DetectionResult(window, null));
        }

        Duration delta = Duration.between(window.getStartTime(), Instant.now());
        DetectionWindow tickedWindow = window.tick(delta);

        boolean microMovementDetected = false;
        List<Double> featureVector = radarSignal.getFeatureVector();
        if (featureVector != null && !featureVector.isEmpty()) {
            double energy = 0.0;
            for (double v : featureVector) {
                energy += v * v;
            }
            energy = Math.sqrt(energy / featureVector.size());
            microMovementDetected = energy > MOTION_THRESHOLD;
        }

        DetectionWindow updatedWindow = microMovementDetected
                ? tickedWindow.incrementCount()
                : tickedWindow;

        LifeDetectedEvent detectedEvent = null;
        if (!updatedWindow.isExpired() && updatedWindow.getMicroMovementCount() >= MIN_MICRO_MOVEMENTS) {
            detectedEvent = new LifeDetectedEvent(
                    new VehicleId("UNKNOWN"),
                    0.8,
                    Instant.now()
            );
            eventPublisher.publish(detectedEvent);
        }

        return Result.ok(new DetectionResult(updatedWindow, detectedEvent));
    }
}
