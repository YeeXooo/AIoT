package com.aiot.application.intervention;

import com.aiot.domain.model.OverrideSignal;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.TripId;

import java.util.List;

public interface IInterventionService {

    Result<ReportOverrideResponse, AppError> reportOverride(DriverId driverId, OverrideSignal signal);

    Result<QueryInterventionResponse, AppError> queryInterventionStatus(TripId tripId);

    Result<QueryInterventionResponse, AppError> queryInterventionHistory(TripId tripId, int page, int size);

    record ReportOverrideResponse(String status, long timestamp, boolean interventionAborted) { }

    record QueryInterventionResponse(String tripId, List<InterventionItem> items, int totalCount, int page, int size) { }

    record InterventionItem(
            String status, String alertType, String riskLevel,
            String instructionType, long timestamp
    ) { }
}
