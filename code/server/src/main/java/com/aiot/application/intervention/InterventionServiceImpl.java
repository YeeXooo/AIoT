package com.aiot.application.intervention;

import com.aiot.domain.intervention.InterventionService;
import com.aiot.domain.model.OverrideSignal;
import com.aiot.domain.model.Trip;
import com.aiot.domain.repository.TripRepository;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.TripId;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InterventionServiceImpl implements IInterventionService {

    private final InterventionService interventionService;
    private final TripRepository tripRepository;

    private final ConcurrentMap<String, List<InterventionRecord>> interventionHistory = new ConcurrentHashMap<>();

    public InterventionServiceImpl(InterventionService interventionService, TripRepository tripRepository) {
        this.interventionService = interventionService;
        this.tripRepository = tripRepository;
    }

    @Override
    public Result<ReportOverrideResponse, AppError> reportOverride(DriverId driverId, OverrideSignal signal) {
        List<Trip> activeTrips = tripRepository.findActiveTrips();
        Optional<Trip> driverTrip = activeTrips.stream()
                .filter(t -> t.driverId().id().equals(driverId.id()))
                .findFirst();

        if (driverTrip.isEmpty()) {
            return Result.err(AppError.notFound("ActiveTrip", "driver " + driverId.id()));
        }

        var result = interventionService.handleOverride(signal);
        boolean aborted = "ABORTED".equals(result.status());

        String tripKey = driverTrip.get().tripId().id();
        InterventionRecord record = new InterventionRecord(
                tripKey, "OVERRIDE", "OVERRIDE", "OVERRIDE",
                "OVERRIDE_" + signal.getType().name(), result.timestamp());
        interventionHistory.computeIfAbsent(tripKey, k -> new ArrayList<>()).add(record);

        return Result.ok(new ReportOverrideResponse(
                result.status().name(), result.timestamp(), aborted));
    }

    @Override
    public Result<QueryInterventionResponse, AppError> queryInterventionStatus(TripId tripId) {
        var tripOpt = tripRepository.findById(tripId.id());
        if (tripOpt.isEmpty()) {
            return Result.err(AppError.notFound("Trip", tripId.id()));
        }

        List<InterventionRecord> records = interventionHistory.getOrDefault(tripId.id(), List.of());
        List<InterventionItem> items = records.stream()
                .sorted((a, b) -> Long.compare(b.timestamp, a.timestamp))
                .limit(1)
                .map(r -> new InterventionItem(
                        r.status, r.alertType, r.riskLevel, r.instructionType, r.timestamp))
                .toList();

        return Result.ok(new QueryInterventionResponse(
                tripId.id(), items, items.size(), 0, 1));
    }

    @Override
    public Result<QueryInterventionResponse, AppError> queryInterventionHistory(
            TripId tripId, int page, int size) {

        var tripOpt = tripRepository.findById(tripId.id());
        if (tripOpt.isEmpty()) {
            return Result.err(AppError.notFound("Trip", tripId.id()));
        }

        List<InterventionRecord> records = interventionHistory.getOrDefault(tripId.id(), List.of());
        List<InterventionRecord> sorted = records.stream()
                .sorted((a, b) -> Long.compare(b.timestamp, a.timestamp))
                .toList();

        int totalCount = sorted.size();
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, totalCount);

        List<InterventionItem> items;
        if (fromIndex < totalCount) {
            items = sorted.subList(fromIndex, toIndex).stream()
                    .map(r -> new InterventionItem(
                            r.status, r.alertType, r.riskLevel, r.instructionType, r.timestamp))
                    .toList();
        } else {
            items = List.of();
        }

        return Result.ok(new QueryInterventionResponse(
                tripId.id(), items, totalCount, page, size));
    }

    private record InterventionRecord(
            String tripId, String status, String alertType,
            String riskLevel, String instructionType, long timestamp
    ) {}
}
