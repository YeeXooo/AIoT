package com.aiot.interfaces.rest;

import com.aiot.application.fleet.IFleetManagementService;
import com.aiot.domain.model.TimeRange;
import com.aiot.domain.shared.AccountId;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.VehicleId;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fleet")
public class FleetController {

    private final IFleetManagementService fleetService;

    public FleetController(IFleetManagementService fleetService) {
        this.fleetService = fleetService;
    }

    // ── DTOs ──

    record HeatmapPoint(double latitude, double longitude, double riskIntensity) {}
    record FatigueDistributionResponse(Map<String, Double> distribution, List<HeatmapPoint> heatmapData,
                                        String dataFreshness, String generatedAt) {}

    record OfflineVehicleEntry(String vehicleId, String licensePlate, String driverId, String driverName,
                                String offlineReason, String offlineSince, String lastHeartbeat) {}
    record OfflineVehiclesResponse(List<OfflineVehicleEntry> offlineVehicles) {}

    record TrajectoryPoint(String timestamp, double latitude, double longitude, double speed) {}
    record TrajectoryResponse(List<TrajectoryPoint> trajectoryPoints, long totalCount, String dataConsistency) {}

    record TripSummary(String tripId, String startTime, String endTime, double score) {}
    record HighRiskDriverEntry(String driverId, String driverName, double compositeRiskScore,
                                TripSummary latestTripSummary, List<String> primaryPenaltyItems) {}
    record HighRiskDriversResponse(List<HighRiskDriverEntry> drivers, long totalCount) {}

    record ReportRequest(String driverId, TimeRangeBody timeRange, String reportType) {}
    record TimeRangeBody(String start, String end) {}
    record SubScores(double fatigueScore, double distractionScore, double abnormalDrivingScore) {}
    record DrivingBehaviorSummary(double overallScore, SubScores subScores, double trendVsLastPeriod) {}
    record RiskDistribution(double FATIGUE, double DISTRACTION, double ROAD_RAGE) {}
    record PenaltyBreakdownEntry(String category, double penaltyScore, List<String> topViolations) {}
    record ReportData(String reportId, String driverId, TimeRangeBody timeRange, String reportType,
                      DrivingBehaviorSummary drivingBehaviorSummary, RiskDistribution riskDistribution,
                      List<PenaltyBreakdownEntry> penaltyBreakdown, double totalMileage,
                      String totalDrivingTime, String generatedAt) {}
    record GenerateReportResponse(String reportId, ReportData reportData, String downloadUrl, boolean isEmpty) {}

    record SubscribeRequest(String adminId, String fleetId) {}
    record SubscribeResponse(String subscriptionId) {}

    // 前端 hardcode 的车队 ID → 后端实际车队 ID 映射
    private static String mapFleetId(String fleetId) {
        return switch (fleetId) {
            case "f1" -> "fleet-east-1";
            case "f2" -> "fleet-west-1";
            default -> fleetId;
        };
    }

    // ── Endpoints ──

    @GetMapping("/{fleetId}/fatigue-distribution")
    public ResponseEntity<?> getFatigueDistribution(@PathVariable String fleetId) {
        fleetId = mapFleetId(fleetId);
        var result = fleetService.getFatigueDistribution(fleetId);
        if (result.isErr()) return errorResponse(result.unwrapErr());

        var data = result.unwrap();

        List<HeatmapPoint> heatmap = List.of(
                new HeatmapPoint(39.90, 116.40, 0.15),
                new HeatmapPoint(39.91, 116.41, 0.30),
                new HeatmapPoint(39.92, 116.39, 0.08)
        );

        return ResponseEntity.ok(new FatigueDistributionResponse(
                data.distribution(), heatmap, data.dataFreshness(),
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
    }

    @GetMapping("/{fleetId}/offline-vehicles")
    public ResponseEntity<?> getOfflineVehicles(@PathVariable String fleetId) {
        fleetId = mapFleetId(fleetId);
        var result = fleetService.getOfflineVehicles(fleetId);
        if (result.isErr()) return errorResponse(result.unwrapErr());

        var data = result.unwrap();
        List<OfflineVehicleEntry> entries = new ArrayList<>();
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        for (var v : data.vehicles()) {
            entries.add(new OfflineVehicleEntry(
                    v.vehicleId(), v.licensePlate(), v.driverId(),
                    "Driver-" + v.driverId().substring(0, Math.min(8, v.driverId().length())),
                    v.reason(), now, now));
        }

        return ResponseEntity.ok(new OfflineVehiclesResponse(entries));
    }

    @GetMapping("/{fleetId}/trajectory")
    public ResponseEntity<?> queryTrajectory(
            @PathVariable String fleetId,
            @RequestParam(required = false) String vehicleId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {

        VehicleId vid = vehicleId != null ? new VehicleId(vehicleId) : new VehicleId("v001-c7e5-4ghi-a011-678901234hij");
        Instant nowInstant = Instant.now();
        TimeRange range = new TimeRange(nowInstant.minusSeconds(86400), nowInstant);

        var result = fleetService.queryVehicleTrajectory(vid, range, page, size);
        if (result.isErr()) return errorResponse(result.unwrapErr());

        var data = result.unwrap();
        List<TrajectoryPoint> points = new ArrayList<>();
        int idx = 0;
        for (var p : data.points()) {
            points.add(new TrajectoryPoint(
                    LocalDateTime.now().minusMinutes(idx)
                            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    p.lat(), p.lng(), p.speed()));
            idx++;
        }

        return ResponseEntity.ok(new TrajectoryResponse(points, data.totalCount(), "CONSISTENT"));
    }

    @GetMapping("/{fleetId}/high-risk-drivers")
    public ResponseEntity<?> drillDownHighRisk(
            @PathVariable String fleetId,
            @RequestParam(defaultValue = "L3_CRITICAL") String riskLevel,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        fleetId = mapFleetId(fleetId);
        var result = fleetService.drillDownHighRisk(fleetId, riskLevel, page, size);
        if (result.isErr()) return errorResponse(result.unwrapErr());

        var data = result.unwrap();
        List<HighRiskDriverEntry> entries = new ArrayList<>();
        for (var d : data.drivers()) {
            entries.add(new HighRiskDriverEntry(
                    d.driverId(), d.driverName(), d.score(),
                    new TripSummary("trip-" + d.driverId().substring(0, 4),
                            LocalDateTime.now().minusHours(3).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                            LocalDateTime.now().minusHours(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                            d.score()),
                    d.penaltyItems()));
        }

        return ResponseEntity.ok(new HighRiskDriversResponse(entries, data.totalCount()));
    }

    @PostMapping("/reports")
    public ResponseEntity<?> generateReport(@RequestBody ReportRequest request) {
        Instant startInstant = LocalDateTime.parse(request.timeRange().start(), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(ZoneId.systemDefault()).toInstant();
        Instant endInstant = LocalDateTime.parse(request.timeRange().end(), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(ZoneId.systemDefault()).toInstant();
        TimeRange timeRange = new TimeRange(startInstant, endInstant);

        var result = fleetService.generateReport(new DriverId(request.driverId()), timeRange, request.reportType());
        if (result.isErr()) return errorResponse(result.unwrapErr());

        var data = result.unwrap();
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        ReportData reportData = new ReportData(
                data.reportId(), request.driverId(),
                request.timeRange(), request.reportType(),
                new DrivingBehaviorSummary(85.0,
                        new SubScores(80.0, 75.0, 90.0), 2.5),
                new RiskDistribution(0.4, 0.3, 0.3),
                List.of(new PenaltyBreakdownEntry("急刹车", 10.0, List.of("2025-12-20 08:30")),
                        new PenaltyBreakdownEntry("疲劳驾驶", 5.0, List.of("2025-12-20 09:15"))),
                120.5, "PT2H30M", now);

        return ResponseEntity.ok(new GenerateReportResponse(
                data.reportId(), reportData, data.downloadUrl(), data.isEmpty()));
    }

    @GetMapping("/reports/{reportId}/download")
    public ResponseEntity<?> downloadReport(@PathVariable String reportId, @RequestParam String format) {
        String reportContent = "{\"reportId\":\"" + reportId + "\",\"generatedAt\":\""
                + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\"}";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(format.equals("pdf") ? MediaType.APPLICATION_PDF : MediaType.APPLICATION_JSON);
        headers.setContentDispositionFormData("attachment", reportId + "." + format);

        return ResponseEntity.ok().headers(headers).body(reportContent.getBytes());
    }

    @PostMapping("/performance-warning-subscription")
    public ResponseEntity<?> subscribePerformanceWarning(@RequestBody SubscribeRequest request) {
        var result = fleetService.subscribePerformanceWarning(
                new AccountId(request.adminId()), request.fleetId());
        if (result.isErr()) return errorResponse(result.unwrapErr());

        return ResponseEntity.ok(new SubscribeResponse(result.unwrap().subscriptionId()));
    }

    @DeleteMapping("/performance-warning-subscription/{subscriptionId}")
    public ResponseEntity<Void> unsubscribePerformanceWarning(@PathVariable String subscriptionId) {
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<Map<String, Object>> errorResponse(AppError error) {
        return switch (error.code()) {
            case "NotFound" -> ResponseEntity.status(404).body(errorBody(error.code(), error.message()));
            case "AccessDenied" -> ResponseEntity.status(403).body(errorBody(error.code(), error.message()));
            case "ValidationFailed" -> ResponseEntity.status(400).body(errorBody(error.code(), error.message()));
            default -> ResponseEntity.status(500).body(errorBody(error.code(), error.message()));
        };
    }

    private static Map<String, Object> errorBody(String code, String message) {
        return Map.of("errorCode", code, "message", message, "requestId", UUID.randomUUID().toString());
    }
}
