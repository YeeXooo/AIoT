package com.aiot.domain.intervention;

import com.aiot.domain.event.DomainEventPublisher;
import com.aiot.domain.event.EmergencyActivatedEvent;
import com.aiot.domain.model.PhysiologicalSnapshot;
import com.aiot.domain.model.TimeRange;
import com.aiot.domain.model.VehicleStateSnapshot;
import com.aiot.domain.port.BufferException;
import com.aiot.domain.port.PhysiologicalDataBuffer;
import com.aiot.domain.port.VehicleStateBuffer;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.Result;

import java.time.Instant;
import java.util.List;

public class EmergencyResponseServiceImpl implements EmergencyResponseService {

    private static final double COLLISION_ACCELERATION_THRESHOLD = 80.0;
    private static final double COLLISION_IMPACT_STRENGTH_THRESHOLD = 0.7;
    private static final double DISABILITY_CONFIDENCE_THRESHOLD = 0.6;
    private static final double HEART_RATE_ABSENT_THRESHOLD = 40.0;
    private static final double BLOOD_OXYGEN_DROP_THRESHOLD = 85.0;
    private static final long PRE_COLLISION_WINDOW_SECONDS = 30;
    private static final long POST_COLLISION_WINDOW_SECONDS = 10;

    private final VehicleStateBuffer vehicleStateBuffer;
    private final PhysiologicalDataBuffer physiologicalDataBuffer;
    private final DomainEventPublisher eventPublisher;

    public EmergencyResponseServiceImpl(VehicleStateBuffer vehicleStateBuffer,
                                         PhysiologicalDataBuffer physiologicalDataBuffer,
                                         DomainEventPublisher eventPublisher) {
        this.vehicleStateBuffer = vehicleStateBuffer;
        this.physiologicalDataBuffer = physiologicalDataBuffer;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Result<DisabilityAssessment, AppError> determineDisability(CollisionImpactSignal signal) {
        if (signal.acceleration() < COLLISION_ACCELERATION_THRESHOLD
                && signal.impactStrength() < COLLISION_IMPACT_STRENGTH_THRESHOLD) {
            return Result.ok(new DisabilityAssessment("No collision detected", 0.0));
        }

        Instant collisionTime = Instant.ofEpochMilli(signal.timestamp());
        TimeRange vehicleWindow = new TimeRange(
                collisionTime.minusSeconds(PRE_COLLISION_WINDOW_SECONDS), collisionTime);
        TimeRange physioWindow = new TimeRange(
                collisionTime.minusSeconds(PRE_COLLISION_WINDOW_SECONDS),
                collisionTime.plusSeconds(POST_COLLISION_WINDOW_SECONDS));

        try {
            List<VehicleStateSnapshot> vehicleSnapshots =
                    vehicleStateBuffer.getSnapshots(null, vehicleWindow);
            List<PhysiologicalSnapshot> physioSnapshots =
                    physiologicalDataBuffer.getReadings(null, physioWindow);

            double disabilityConfidence = computeDisabilityConfidence(physioSnapshots, vehicleSnapshots);

            if (disabilityConfidence >= DISABILITY_CONFIDENCE_THRESHOLD) {
                EmergencyActivatedEvent event = new EmergencyActivatedEvent(
                        new DriverId(signal.driverId()), 0.0, 0.0, vehicleSnapshots, collisionTime);
                eventPublisher.publish(event);
                return Result.ok(new DisabilityAssessment("Driver likely disabled", disabilityConfidence));
            }

            return Result.ok(new DisabilityAssessment("Driver responsive", disabilityConfidence));
        } catch (BufferException e) {
            return Result.err(AppError.invalidState("Buffer retrieval failed: " + e.getMessage()));
        }
    }

    private double computeDisabilityConfidence(List<PhysiologicalSnapshot> physioSnapshots,
                                                List<VehicleStateSnapshot> vehicleSnapshots) {
        if (physioSnapshots.isEmpty()) {
            return vehicleSnapshots.stream().anyMatch(v ->
                    v.acceleration() != null && v.acceleration() > COLLISION_ACCELERATION_THRESHOLD) ? 0.5 : 0.0;
        }

        double heartRateSum = 0;
        double bloodOxygenSum = 0;
        int count = 0;
        long abnormalCount = 0;

        for (PhysiologicalSnapshot snap : physioSnapshots) {
            if (snap.heartRate() != null) {
                heartRateSum += snap.heartRate();
                if (snap.heartRate() < HEART_RATE_ABSENT_THRESHOLD) {
                    abnormalCount++;
                }
            }
            if (snap.bloodOxygen() != null) {
                bloodOxygenSum += snap.bloodOxygen();
                if (snap.bloodOxygen() < BLOOD_OXYGEN_DROP_THRESHOLD) {
                    abnormalCount++;
                }
            }
            count++;
        }

        if (count == 0) {
            return 0.3;
        }

        double avgHeartRate = heartRateSum / count;
        double avgBloodOxygen = bloodOxygenSum / count;
        double abnormalRatio = (double) abnormalCount / (count * 2);

        double severityScore = 0.0;
        if (avgHeartRate < HEART_RATE_ABSENT_THRESHOLD) {
            severityScore += 0.4;
        }
        if (avgBloodOxygen < BLOOD_OXYGEN_DROP_THRESHOLD) {
            severityScore += 0.3;
        }
        severityScore += abnormalRatio * 0.3;

        return Math.min(severityScore, 1.0);
    }
}
