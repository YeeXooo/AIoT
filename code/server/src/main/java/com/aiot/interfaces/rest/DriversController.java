package com.aiot.interfaces.rest;

import com.aiot.application.risk.IRiskMonitoringService;
import com.aiot.domain.repository.TripRepository;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.Result;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/drivers")
public class DriversController {

    private final IRiskMonitoringService riskMonitoringService;
    private final TripRepository tripRepository;

    public DriversController(IRiskMonitoringService riskMonitoringService,
                             TripRepository tripRepository) {
        this.riskMonitoringService = riskMonitoringService;
        this.tripRepository = tripRepository;
    }

    record ActiveAlertEntry(String alertType, String riskLevel) {}
    record RiskStatusResponse(boolean hasActiveTrip, List<ActiveAlertEntry> activeAlerts, String derivedStatusColor) {}
    record AlertItem(String alertId, String alertType, String riskLevel, String occurredAt, String resolvedAt,
                     String tripId, Map<String, Double> gpsLocation) {}
    record AlertHistoryResponse(List<AlertItem> alerts, long totalCount) {}

    @GetMapping("/{driverId}/risk-status")
    public ResponseEntity<?> getRiskStatus(@PathVariable String driverId) {
        Result<IRiskMonitoringService.GetDriverRiskStatusResponse, AppError> result =
                riskMonitoringService.getDriverRiskStatus(new DriverId(driverId));

        if (result.isErr()) {
            return errorResponse(result.unwrapErr());
        }

        var status = result.unwrap();
        boolean hasActiveTrip = tripRepository.findActiveTrips().stream()
                .anyMatch(t -> t.driverId().id().equals(driverId));

        List<ActiveAlertEntry> activeAlerts = new ArrayList<>();
        for (var risk : status.activeRisks()) {
            activeAlerts.add(new ActiveAlertEntry(risk.alertType(), risk.riskLevel()));
        }

        return ResponseEntity.ok(new RiskStatusResponse(
                hasActiveTrip, activeAlerts, status.statusColor()));
    }

    @GetMapping("/{driverId}/alerts")
    public ResponseEntity<?> queryAlertHistory(
            @PathVariable String driverId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String alertType,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        LocalDateTime fromDateTime = from != null ? LocalDateTime.parse(from, DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null;
        LocalDateTime toDateTime = to != null ? LocalDateTime.parse(to, DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null;

        Result<IRiskMonitoringService.QueryAlertHistoryResponse, AppError> result =
                riskMonitoringService.queryAlertHistory(
                        new DriverId(driverId), fromDateTime, toDateTime,
                        alertType, riskLevel, page, size);

        if (result.isErr()) {
            return errorResponse(result.unwrapErr());
        }

        var history = result.unwrap();
        List<AlertItem> alerts = new ArrayList<>();
        for (var item : history.items()) {
            alerts.add(new AlertItem(
                    item.alertId(), item.alertType(), item.riskLevel(),
                    item.occurredAt() != null ? item.occurredAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null,
                    null,
                    item.tripId(),
                    null
            ));
        }

        return ResponseEntity.ok(new AlertHistoryResponse(alerts, history.totalCount()));
    }

    private ResponseEntity<Map<String, Object>> errorResponse(AppError error) {
        return switch (error.code()) {
            case "NotFound" -> ResponseEntity.status(404).body(errorBody(error.code(), error.message()));
            case "AccessDenied" -> ResponseEntity.status(403).body(errorBody(error.code(), error.message()));
            case "InvalidState" -> ResponseEntity.status(409).body(errorBody(error.code(), error.message()));
            default -> ResponseEntity.status(400).body(errorBody(error.code(), error.message()));
        };
    }

    private static Map<String, Object> errorBody(String code, String message) {
        return Map.of("errorCode", code, "message", message, "requestId", UUID.randomUUID().toString());
    }
}
