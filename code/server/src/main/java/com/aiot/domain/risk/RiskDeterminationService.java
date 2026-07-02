package com.aiot.domain.risk;

import com.aiot.domain.event.RiskDeterminedEvent;
import com.aiot.domain.event.RiskResolvedEvent;
import com.aiot.domain.model.SensorReading;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;

import java.util.List;

public interface RiskDeterminationService {

    Result<DeterminationResult, AppError> executeStreamFusion(
            List<SensorReading> readings, ActiveRiskSet currentRiskSet);

    record DeterminationResult(
            ActiveRiskSet updatedRiskSet,
            List<RiskDeterminedEvent> determinedEvents,
            List<RiskResolvedEvent> resolvedEvents
    ) { }
}
