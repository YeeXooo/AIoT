package com.aiot.interfaces.rest;

import com.aiot.application.GuardianshipApplicationService;
import com.aiot.application.guardianship.IRemoteGuardianshipService;
import com.aiot.domain.shared.AccountId;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.Result;
import com.aiot.infra.persistence.GuardianshipEntity;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
@RequestMapping("/api/v1/guardianship")
public class GuardianshipController {

    private final GuardianshipApplicationService service;
    private final IRemoteGuardianshipService guardianshipService;

    public GuardianshipController(GuardianshipApplicationService service,
                                  IRemoteGuardianshipService guardianshipService) {
        this.service = service;
        this.guardianshipService = guardianshipService;
    }

    // ── Original CRUD endpoints ──

    @GetMapping("/list")
    public List<GuardianshipEntity> list(@RequestParam(required = false) String driverId,
                                         @RequestParam(required = false) String accountId) {
        if (driverId != null) { return service.findByDriver(driverId); }
        if (accountId != null) { return service.findAll(); }
        return service.findAll();
    }

    @PostMapping
    public GuardianshipEntity create(@RequestBody GuardianshipEntity entity) {
        return service.create(entity);
    }

    @DeleteMapping("/{driverId}/{accountId}")
    public ResponseEntity<Void> revoke(@PathVariable String driverId,
                                        @PathVariable String accountId) {
        service.revoke(driverId, accountId);
        return ResponseEntity.noContent().build();
    }

    // ── New frontend-compatible endpoints ──

    // DTOs
    record BindRequest(String familyAccountId, String driverId) {}
    record MediaSessionRequest(String familyAccountId, String driverId, String sessionType,
                                String secondaryAuthToken) {}
    record MediaSessionResponse(String sessionHandle, String sessionToken, String sparkRTCRoomId,
                                 String sparkRTCJoinToken) {}
    record NotificationPreferenceRequest(String familyAccountId, String driverId,
                                          List<String> preferredRiskLevels) {}
    record ManualRescueRequest(String familyAccountId, String driverId, String secondaryAuthToken) {}
    record ManualRescueResponse(String rescueRequestId, String rescueReportId, String status) {}
    record WindowControlRequest(String familyAccountId, String driverId, String windowOperation,
                                 String windowPosition, String secondaryAuthToken) {}
    record PermissionEntry(String permissionType, boolean granted, String grantedAt, String expiresAt) {}
    record CareRelationship(String status, String establishedAt) {}
    record PermissionsResponse(String familyAccountId, String driverId,
                                List<PermissionEntry> permissions, CareRelationship careRelationship) {}
    record WindowStatusEntry(String windowPosition, String state, String lastOperation,
                              String lastOperationResult, String updatedAt) {}
    record WindowStatusResponse(List<WindowStatusEntry> windowStatuses) {}

    /** POST /guardianship/bind */
    @PostMapping("/bind")
    public ResponseEntity<?> bindDriver(@RequestBody BindRequest request) {
        String authAccountId = getCurrentAccountId();
        var result = guardianshipService.subscribeDriverStatus(
                new AccountId(authAccountId), new DriverId(request.driverId()));
        if (result.isErr()) return errorResponse(result.unwrapErr());
        return ResponseEntity.ok(Map.of("status", "bound", "accountId", authAccountId,
                "driverId", request.driverId()));
    }

    /** POST /guardianship/media-session */
    @PostMapping("/media-session")
    public ResponseEntity<?> requestMediaSession(@RequestBody MediaSessionRequest request) {
        String authAccountId = getCurrentAccountId();
        var result = guardianshipService.requestMediaSession(
                new AccountId(authAccountId), new DriverId(request.driverId()),
                request.sessionType());
        if (result.isErr()) return errorResponse(result.unwrapErr());

        var data = result.unwrap();
        return ResponseEntity.ok(new MediaSessionResponse(
                data.sessionHandle(),
                UUID.randomUUID().toString(),
                "room-" + UUID.randomUUID().toString().substring(0, 8),
                UUID.randomUUID().toString()));
    }

    /** DELETE /guardianship/media-session/{sessionHandle} */
    @DeleteMapping("/media-session/{sessionHandle}")
    public ResponseEntity<Void> endMediaSession(@PathVariable String sessionHandle) {
        guardianshipService.endMediaSession(sessionHandle);
        return ResponseEntity.noContent().build();
    }

    /** PUT /guardianship/notification-preference */
    @PutMapping("/notification-preference")
    public ResponseEntity<?> updateNotificationPreference(@RequestBody NotificationPreferenceRequest request) {
        String authAccountId = getCurrentAccountId();
        var result = guardianshipService.updateNotificationPreference(
                new AccountId(authAccountId), new DriverId(request.driverId()),
                request.preferredRiskLevels());
        if (result.isErr()) return errorResponse(result.unwrapErr());
        return ResponseEntity.ok(Map.of("status", "updated"));
    }

    /** POST /guardianship/manual-rescue */
    @PostMapping("/manual-rescue")
    public ResponseEntity<?> triggerManualRescue(@RequestBody ManualRescueRequest request) {
        String authAccountId = getCurrentAccountId();
        var result = guardianshipService.triggerManualRescue(
                new AccountId(authAccountId), new DriverId(request.driverId()));
        if (result.isErr()) return errorResponse(result.unwrapErr());

        var data = result.unwrap();
        return ResponseEntity.ok(new ManualRescueResponse(
                "rescue-req-" + UUID.randomUUID().toString().substring(0, 8),
                data.rescueReportId(), data.status()));
    }

    /** POST /guardianship/window-control */
    @PostMapping("/window-control")
    public ResponseEntity<?> controlVehicleWindow(@RequestBody WindowControlRequest request) {
        String authAccountId = getCurrentAccountId();
        var result = guardianshipService.controlVehicleWindow(
                new AccountId(authAccountId), new DriverId(request.driverId()),
                request.windowPosition());
        if (result.isErr()) return errorResponse(result.unwrapErr());
        return ResponseEntity.ok(Map.of("status", "controlled", "windowPosition", request.windowPosition()));
    }

    /** GET /guardianship/{driverId}/permissions */
    @GetMapping("/{driverId}/permissions")
    public ResponseEntity<?> queryGuardianshipPermissions(@PathVariable String driverId) {
        String authAccountId = getCurrentAccountId();
        var result = guardianshipService.queryGuardianshipPermissions(
                new AccountId(authAccountId), new DriverId(driverId));
        if (result.isErr()) return errorResponse(result.unwrapErr());

        var data = result.unwrap();
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        List<PermissionEntry> perms = new ArrayList<>();
        for (String p : data.permissions()) {
            perms.add(new PermissionEntry(p, true, now, null));
        }

        return ResponseEntity.ok(new PermissionsResponse(
                data.accountId(), data.driverId(), perms,
                new CareRelationship(data.isRevoked() ? "REVOKED" : "ACTIVE", now)));
    }

    // ── Helpers ──

    private String getCurrentAccountId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            return auth.getName();
        }
        return "anonymous";
    }

    private ResponseEntity<Map<String, Object>> errorResponse(AppError error) {
        return switch (error.code()) {
            case "NotFound" -> ResponseEntity.status(404).body(errorBody(error.code(), error.message()));
            case "AccessDenied" -> ResponseEntity.status(403).body(errorBody(error.code(), error.message()));
            case "ValidationFailed" -> ResponseEntity.status(400).body(errorBody(error.code(), error.message()));
            case "InvalidState" -> ResponseEntity.status(409).body(errorBody(error.code(), error.message()));
            default -> ResponseEntity.status(500).body(errorBody(error.code(), error.message()));
        };
    }

    private static Map<String, Object> errorBody(String code, String message) {
        return Map.of("errorCode", code, "message", message, "requestId", UUID.randomUUID().toString());
    }
}
