package com.aiot.domain.intervention;

import com.aiot.domain.event.AlertType;
import com.aiot.domain.event.RiskLevel;
import com.aiot.domain.model.InterventionInstruction;
import com.aiot.domain.model.OverrideSignal;

import java.util.List;

public interface InterventionService {
    enum OverrideResult { ABORTED, CONTINUING, RESUMED }

    List<InterventionInstruction> generateIntervention(AlertType alertType, RiskLevel riskLevel);

    InterventionResult handleOverride(OverrideSignal signal);

    record InterventionResult(OverrideResult status, long timestamp) { }
}
