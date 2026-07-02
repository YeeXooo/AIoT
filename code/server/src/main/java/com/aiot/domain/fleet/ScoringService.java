package com.aiot.domain.fleet;

import com.aiot.domain.model.TimeRange;
import com.aiot.domain.model.TripScore;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;

public interface ScoringService {
    Result<TripScore, AppError> calculateTripScore(String tripId);

    Result<Double, AppError> calculatePeriodScore(String driverId, TimeRange timeRange);

    Result<Double, AppError> calculateDriverCompositeScore(String driverId);
}
