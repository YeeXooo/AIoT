package com.aiot.domain.intervention;

import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;

public interface EmergencyResponseService {
    Result<DisabilityAssessment, AppError> determineDisability(CollisionImpactSignal signal);

    record DisabilityAssessment(String conclusion, double confidence) {}

    record CollisionImpactSignal(double acceleration, double impactStrength, long timestamp) {}
}
