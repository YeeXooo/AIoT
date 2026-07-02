package com.aiot.application.guardianship;

import com.aiot.domain.emergency.EmergencyRescueService;
import com.aiot.domain.event.RiskLevel;
import com.aiot.domain.guardianship.DriverStatusBroadcastService;
import com.aiot.domain.guardianship.PermissionService;
import com.aiot.domain.model.NotificationPreference;
import com.aiot.domain.model.Permission;
import com.aiot.domain.port.MediaSessionPort;
import com.aiot.domain.port.NotificationPort;
import com.aiot.domain.repository.SystemAccountRepository;
import com.aiot.domain.shared.AccountId;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.Result;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RemoteGuardianshipServiceImpl implements IRemoteGuardianshipService {

    private final PermissionService permissionService;
    private final DriverStatusBroadcastService driverStatusBroadcastService;
    private final EmergencyRescueService emergencyRescueService;
    private final SystemAccountRepository systemAccountRepository;
    private final MediaSessionPort mediaSessionPort;
    private final NotificationPort notificationPort;

    private final Map<String, MediaSessionPort.SessionHandle> activeMediaSessions = new ConcurrentHashMap<>();

    public RemoteGuardianshipServiceImpl(
            PermissionService permissionService,
            DriverStatusBroadcastService driverStatusBroadcastService,
            EmergencyRescueService emergencyRescueService,
            SystemAccountRepository systemAccountRepository,
            MediaSessionPort mediaSessionPort,
            NotificationPort notificationPort) {
        this.permissionService = permissionService;
        this.driverStatusBroadcastService = driverStatusBroadcastService;
        this.emergencyRescueService = emergencyRescueService;
        this.systemAccountRepository = systemAccountRepository;
        this.mediaSessionPort = mediaSessionPort;
        this.notificationPort = notificationPort;
    }

    @Override
    public Result<SubscribeResponse, AppError> subscribeDriverStatus(AccountId accountId, DriverId driverId) {
        var accountOpt = systemAccountRepository.findById(accountId.id());
        if (accountOpt.isEmpty()) {
            return Result.err(AppError.notFound("SystemAccount", accountId.id()));
        }
        if (!accountOpt.get().isActive()) {
            return Result.err(AppError.invalidState("Account " + accountId.id() + " is not active"));
        }

        var grantResult = permissionService.grantAccess(driverId.id(), accountId.id(), "SUBSCRIBE_STATUS");
        if (grantResult.isErr()) {
            return Result.err(grantResult.unwrapErr());
        }

        driverStatusBroadcastService.broadcastStatus(driverId.id());

        return Result.ok(new SubscribeResponse(accountId.id(), driverId.id(), "SUBSCRIBED"));
    }

    @Override
    public Result<Void, AppError> unsubscribeDriverStatus(AccountId accountId, DriverId driverId) {
        var revokeResult = permissionService.revokeAccess(driverId.id(), accountId.id(), "UNSUBSCRIBE_STATUS");
        if (revokeResult.isErr()) {
            return Result.err(revokeResult.unwrapErr());
        }

        return Result.ok(null);
    }

    @Override
    public Result<MediaSessionResponse, AppError> requestMediaSession(
            AccountId accountId, DriverId driverId, String sessionType) {

        var accountOpt = systemAccountRepository.findById(accountId.id());
        if (accountOpt.isEmpty()) {
            return Result.err(AppError.notFound("SystemAccount", accountId.id()));
        }

        var permResult = permissionService.checkPermission(driverId.id(), accountId.id());
        if (permResult.isErr()) {
            return Result.err(permResult.unwrapErr());
        }
        if (permResult.unwrap().isRevoked()) {
            return Result.err(AppError.accessDenied("Account " + accountId.id()
                    + " does not have permission for driver " + driverId.id()));
        }

        MediaSessionPort.SessionType type;
        try {
            type = MediaSessionPort.SessionType.valueOf(sessionType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Result.err(AppError.validationFailed(
                    "Invalid session type: " + sessionType + ". Valid values: AUDIO, VIDEO"));
        }

        try {
            MediaSessionPort.SessionHandle handle = mediaSessionPort.establishSession(accountId, driverId, type);
            activeMediaSessions.put(handle.sessionId(), handle);

            return Result.ok(new MediaSessionResponse(
                    handle.sessionId(), sessionType.toUpperCase(), driverId.id(), "ESTABLISHED"));
        } catch (MediaSessionPort.MediaSessionException e) {
            return Result.err(AppError.invalidState(
                    "Media session establishment failed: " + e.getMessage()));
        }
    }

    @Override
    public Result<Void, AppError> endMediaSession(String sessionHandle) {
        MediaSessionPort.SessionHandle handle = activeMediaSessions.remove(sessionHandle);
        if (handle == null) {
            return Result.err(AppError.notFound("MediaSession", sessionHandle));
        }

        try {
            mediaSessionPort.terminateSession(handle);
        } catch (MediaSessionPort.MediaSessionException e) {
            return Result.err(AppError.invalidState(
                    "Media session termination failed: " + e.getMessage()));
        }

        return Result.ok(null);
    }

    @Override
    public Result<Void, AppError> updateNotificationPreference(
            AccountId accountId, DriverId driverId, List<String> riskLevels) {

        var accountOpt = systemAccountRepository.findById(accountId.id());
        if (accountOpt.isEmpty()) {
            return Result.err(AppError.notFound("SystemAccount", accountId.id()));
        }

        Set<RiskLevel> levels;
        try {
            levels = riskLevels == null || riskLevels.isEmpty()
                    ? Set.of(RiskLevel.values())
                    : riskLevels.stream()
                            .map(String::toUpperCase)
                            .map(RiskLevel::valueOf)
                            .collect(java.util.stream.Collectors.toSet());
        } catch (IllegalArgumentException e) {
            return Result.err(AppError.validationFailed("Invalid risk level in: " + riskLevels));
        }

        var account = accountOpt.get();
        account.updateNotificationPreference(NotificationPreference.of(levels));
        systemAccountRepository.save(account);

        return Result.ok(null);
    }

    @Override
    public Result<TriggerRescueResponse, AppError> triggerManualRescue(
            AccountId accountId, DriverId driverId) {

        var accountOpt = systemAccountRepository.findById(accountId.id());
        if (accountOpt.isEmpty()) {
            return Result.err(AppError.notFound("SystemAccount", accountId.id()));
        }

        var permResult = permissionService.checkPermission(driverId.id(), accountId.id());
        if (permResult.isErr()) {
            return Result.err(permResult.unwrapErr());
        }
        if (permResult.unwrap().isRevoked()) {
            return Result.err(AppError.accessDenied("Account " + accountId.id()
                    + " does not have guardianship permission for driver " + driverId.id()));
        }

        var rescueResult = emergencyRescueService.triggerManualRescue(driverId, accountId);
        if (rescueResult.isErr()) {
            return Result.err(rescueResult.unwrapErr());
        }

        try {
            NotificationPort.NotificationPayload payload = new NotificationPort.NotificationPayload(
                    NotificationPort.NotificationType.RESCUE_REPORT,
                    "Manual Rescue Triggered",
                    "Manual rescue triggered for driver " + driverId.id() + " by account " + accountId.id(),
                    NotificationPort.NotificationPriority.URGENT);
            notificationPort.pushNotification(accountId, payload);
        } catch (NotificationPort.NotificationException ignored) {
        }

        return Result.ok(new TriggerRescueResponse("rescue-" + driverId.id(), "TRIGGERED"));
    }

    @Override
    public Result<GuardianshipPermissionsResponse, AppError> queryGuardianshipPermissions(
            AccountId accountId, DriverId driverId) {

        var accountOpt = systemAccountRepository.findById(accountId.id());
        if (accountOpt.isEmpty()) {
            return Result.err(AppError.notFound("SystemAccount", accountId.id()));
        }

        var permResult = permissionService.checkPermission(driverId.id(), accountId.id());
        if (permResult.isErr()) {
            return Result.err(permResult.unwrapErr());
        }

        var permission = permResult.unwrap();
        List<String> operations = permission.getOperations().stream().toList();

        return Result.ok(new GuardianshipPermissionsResponse(
                accountId.id(), driverId.id(), operations, permission.isRevoked()));
    }
}
