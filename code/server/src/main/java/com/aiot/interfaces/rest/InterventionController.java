package com.aiot.interfaces.rest;

import com.aiot.application.intervention.IInterventionService;
import com.aiot.domain.model.OverrideSignal;
import com.aiot.domain.model.OverrideType;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.TripId;
import com.aiot.domain.repository.TripRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trips")
public class InterventionController {

    private final IInterventionService interventionService;
    private final TripRepository tripRepository;

    public InterventionController(IInterventionService interventionService,
                                  TripRepository tripRepository) {
        this.interventionService = interventionService;
        this.tripRepository = tripRepository;
    }

    record OverrideRequest(String type, long timestamp) {}
    record OverrideResponse(boolean accepted, String message) {}
    record InstructionEntry(String type, Map<String, String> params, String description) {}
    record InterventionStatusResponse(String tripId, String riskLevel,
                                      List<InstructionEntry> activeInstructions) {}

    @GetMapping("/{tripId}/interventions/active")
    public ResponseEntity<?> queryInterventionStatus(@PathVariable String tripId) {
        var result = interventionService.queryInterventionStatus(new TripId(tripId));
        if (result.isErr()) {
            return errorResponse(result.unwrapErr());
        }

        var data = result.unwrap();
        List<InstructionEntry> instructions = new ArrayList<>();
        for (var item : data.items()) {
            String type = item.instructionType();
            if (type.startsWith("OVERRIDE_")) {
                continue;
            }
            instructions.add(new InstructionEntry(
                    type, Collections.emptyMap(), type));
        }

        return ResponseEntity.ok(new InterventionStatusResponse(
                tripId, instructions.isEmpty() ? "NONE" : deriveRiskLevel(data.items()), instructions));
    }

    @PostMapping("/{tripId}/override")
    public ResponseEntity<?> reportOverride(@PathVariable String tripId,
                                            @RequestBody OverrideRequest request) {
        OverrideType overrideType = mapOverrideType(request.type());
        if (overrideType == null) {
            return ResponseEntity.badRequest().body(
                    errorBody("ValidationFailed", "Unknown override type: " + request.type()));
        }

        var tripOpt = tripRepository.findById(tripId);
        if (tripOpt.isEmpty()) {
            return errorResponse(AppError.notFound("Trip", tripId));
        }

        DriverId driverId = tripOpt.get().driverId();
        OverrideSignal signal = OverrideSignal.of(overrideType,
                Instant.ofEpochMilli(request.timestamp()));

        var result = interventionService.reportOverride(driverId, signal);
        if (result.isErr()) {
            return errorResponse(result.unwrapErr());
        }

        var data = result.unwrap();
        return ResponseEntity.ok(new OverrideResponse(
                data.interventionAborted(),
                data.interventionAborted() ? "干预已中止" : "干预继续执行"));
    }

    private OverrideType mapOverrideType(String type) {
        return switch (type.toUpperCase()) {
            case "STEER" -> OverrideType.TURNING;
            case "BRAKE" -> OverrideType.BRAKING;
            case "ACCELERATE" -> OverrideType.ACCELERATING;
            case "RESUME" -> OverrideType.RESUMING;
            case "TURNING" -> OverrideType.TURNING;
            case "BRAKING" -> OverrideType.BRAKING;
            case "ACCELERATING" -> OverrideType.ACCELERATING;
            case "RESUMING" -> OverrideType.RESUMING;
            default -> null;
        };
    }

    private String deriveRiskLevel(List<?> items) {
        if (items.isEmpty()) {
            return "NONE";
        }
        for (var item : items) {
            String name = item instanceof IInterventionService.InterventionItem i
                    ? i.riskLevel() : "";
            if ("L3_CRITICAL".equals(name)) {
                return "L3";
            }
        }
        for (var item : items) {
            String name = item instanceof IInterventionService.InterventionItem i
                    ? i.riskLevel() : "";
            if ("L2_WARNING".equals(name)) {
                return "L2";
            }
        }
        return "L1";
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
