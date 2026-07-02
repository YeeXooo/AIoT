package com.aiot.domain.fleet;

import com.aiot.domain.event.DomainEventPublisher;
import com.aiot.domain.event.DriverScoreUpdatedEvent;
import com.aiot.domain.event.PerformanceWarningEvent;
import com.aiot.domain.event.TripScoredEvent;
import com.aiot.domain.model.DriverComprehensiveScore;
import com.aiot.domain.model.Trip;
import com.aiot.domain.model.TripScore;
import com.aiot.domain.model.TimeRange;
import com.aiot.domain.repository.DriverRepository;
import com.aiot.domain.repository.TripRepository;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public class ScoringServiceImpl implements ScoringService {

    private static final double HEAVY_FATIGUE_WEIGHT = 10.0;
    private static final double DISTRACTION_WEIGHT = 5.0;
    private static final double ROAD_RAGE_WEIGHT = 8.0;
    private static final double HARD_BRAKING_WEIGHT = 2.0;
    private static final double HARD_ACCELERATION_WEIGHT = 2.0;

    private final TripRepository tripRepository;
    private final DriverRepository driverRepository;
    private final DomainEventPublisher eventPublisher;

    public ScoringServiceImpl(TripRepository tripRepository, DriverRepository driverRepository,
                               DomainEventPublisher eventPublisher) {
        this.tripRepository = tripRepository;
        this.driverRepository = driverRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Result<TripScore, AppError> calculateTripScore(String tripId) {
        return tripRepository.findById(tripId)
                .map(trip -> {
                    int heavyFatigue = trip.drivingBehaviorCounters().getHeavyFatigueCount();
                    int distraction = trip.drivingBehaviorCounters().getDistractionCount();
                    int roadRage = trip.drivingBehaviorCounters().getRoadRageCount();
                    int hardBraking = trip.drivingBehaviorCounters().getSuddenBrakingCount();
                    int hardAcceleration = trip.drivingBehaviorCounters().getSuddenAccelerationCount();

                    int score = computeScore(heavyFatigue, distraction, roadRage,
                            hardBraking, hardAcceleration);
                    TripScore tripScore = TripScore.of(score);
                    trip.updateTripScore(tripScore);
                    tripRepository.save(trip);

                    eventPublisher.publish(new TripScoredEvent(
                            trip.tripId(), score,
                            heavyFatigue, distraction, roadRage,
                            hardBraking, hardAcceleration
                    ));

                    if (score < 60) {
                        eventPublisher.publish(new PerformanceWarningEvent(
                                trip.driverId(), score, "TRIP",
                                "fatigue=" + heavyFatigue + ", distraction=" + distraction
                                        + ", roadRage=" + roadRage,
                                Instant.now()
                        ));
                    }

                    return Result.<TripScore, AppError>ok(tripScore);
                })
                .orElseGet(() -> Result.err(AppError.notFound("Trip", tripId)));
    }

    @Override
    public Result<Double, AppError> calculatePeriodScore(String driverId, TimeRange timeRange) {
        List<Trip> trips = tripRepository.findByDriverId(driverId);
        if (trips.isEmpty()) {
            return Result.ok(100.0);
        }

        double weightedScoreSum = 0;
        long totalDurationMs = 0;
        for (Trip trip : trips) {
            if (trip.startedAt() == null) {
                continue;
            }
            long tripEpoch = trip.startedAt().atZone(ZoneId.systemDefault()).toEpochSecond();
            long fromEpoch = timeRange.from().getEpochSecond();
            long toEpoch = timeRange.to().getEpochSecond();
            if (tripEpoch >= fromEpoch && tripEpoch <= toEpoch) {
                int score = computeScore(
                        trip.drivingBehaviorCounters().getHeavyFatigueCount(),
                        trip.drivingBehaviorCounters().getDistractionCount(),
                        trip.drivingBehaviorCounters().getRoadRageCount(),
                        trip.drivingBehaviorCounters().getSuddenBrakingCount(),
                        trip.drivingBehaviorCounters().getSuddenAccelerationCount()
                );
                LocalDateTime end = trip.endedAt().orElse(LocalDateTime.now());
                long durationMs = Duration.between(trip.startedAt(), end).toMillis();
                weightedScoreSum += score * durationMs;
                totalDurationMs += durationMs;
            }
        }

        if (totalDurationMs == 0) {
            return Result.ok(100.0);
        }
        double result = weightedScoreSum / totalDurationMs;
        return Result.ok(Math.max(0, Math.min(100, result)));
    }

    @Override
    public Result<Double, AppError> calculateDriverCompositeScore(String driverId) {
        return driverRepository.findById(driverId)
                .map(driver -> {
                    int oldScore = driver.comprehensiveScore()
                            .map(DriverComprehensiveScore::getValue)
                            .orElse(0);
                    List<Trip> trips = tripRepository.findByDriverId(driverId);
                    double composite = computeDriverComposite(trips);
                    int newScoreValue = (int) Math.round(composite);
                    DriverComprehensiveScore score = DriverComprehensiveScore.of(newScoreValue);
                    driver.updateComprehensiveScore(score);
                    driverRepository.save(driver);

                    eventPublisher.publish(new DriverScoreUpdatedEvent(
                            driver.driverId(), newScoreValue, oldScore,
                            "COMPOSITE", Instant.now()
                    ));

                    return Result.<Double, AppError>ok(composite);
                })
                .orElseGet(() -> Result.err(AppError.notFound("Driver", driverId)));
    }

    private int computeScore(int heavyFatigue, int distraction, int roadRage,
                              int hardBraking, int hardAcceleration) {
        double penalty = heavyFatigue * HEAVY_FATIGUE_WEIGHT
                + distraction * DISTRACTION_WEIGHT
                + roadRage * ROAD_RAGE_WEIGHT
                + hardBraking * HARD_BRAKING_WEIGHT
                + hardAcceleration * HARD_ACCELERATION_WEIGHT;
        return Math.max(0, 100 - (int) Math.round(penalty));
    }

    private double computeDriverComposite(List<Trip> trips) {
        if (trips.isEmpty()) {
            return 100.0;
        }
        double total = 0;
        int count = 0;
        for (Trip trip : trips) {
            int score = computeScore(
                    trip.drivingBehaviorCounters().getHeavyFatigueCount(),
                    trip.drivingBehaviorCounters().getDistractionCount(),
                    trip.drivingBehaviorCounters().getRoadRageCount(),
                    trip.drivingBehaviorCounters().getSuddenBrakingCount(),
                    trip.drivingBehaviorCounters().getSuddenAccelerationCount()
            );
            total += score;
            count++;
        }
        return count > 0 ? total / count : 100.0;
    }
}
