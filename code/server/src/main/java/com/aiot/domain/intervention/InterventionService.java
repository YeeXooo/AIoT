package com.aiot.domain.intervention;

import com.aiot.domain.event.AlertType;
import com.aiot.domain.event.RiskLevel;
import com.aiot.domain.model.InterventionInstruction;
import com.aiot.domain.model.OverrideSignal;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;

import java.util.List;

public interface InterventionService {
    List<InterventionInstruction> generateIntervention(AlertType alertType, RiskLevel riskLevel);

    InterventionResult handleOverride(OverrideSignal signal);

    record InterventionResult(String status, long timestamp) {}
}
