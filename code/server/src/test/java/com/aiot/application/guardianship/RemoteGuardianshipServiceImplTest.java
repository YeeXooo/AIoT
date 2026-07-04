package com.aiot.application.guardianship;

import com.aiot.domain.emergency.EmergencyRescueService;
import com.aiot.domain.event.RiskLevel;
import com.aiot.domain.guardianship.DriverStatusBroadcastService;
import com.aiot.domain.guardianship.PermissionService;
import com.aiot.domain.model.AccountRole;
import com.aiot.domain.model.Permission;
import com.aiot.domain.model.SystemAccount;
import com.aiot.domain.port.MediaSessionPort;
import com.aiot.domain.port.NotificationPort;
import com.aiot.domain.repository.SystemAccountRepository;
import com.aiot.domain.shared.AccountId;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.Result;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RemoteGuardianshipServiceImplTest {

    @Mock private PermissionService permissionService;
    @Mock private DriverStatusBroadcastService driverStatusBroadcastService;
    @Mock private EmergencyRescueService emergencyRescueService;
    @Mock private SystemAccountRepository systemAccountRepository;
    @Mock private MediaSessionPort mediaSessionPort;
    @Mock private NotificationPort notificationPort;
    @Mock private com.aiot.infra.repository.GuardianshipJpaRepository guardianshipJpaRepository;
    @Mock private com.aiot.application.PendingFamilyRequestStore pendingFamilyRequestStore;

    private RemoteGuardianshipServiceImpl service;
    private final AccountId accountId = new AccountId("acc-1");
    private final DriverId driverId = new DriverId("drv-1");

    @BeforeEach
    void setUp() {
        service = new RemoteGuardianshipServiceImpl(
                permissionService, driverStatusBroadcastService, emergencyRescueService,
                systemAccountRepository, mediaSessionPort, notificationPort,
                guardianshipJpaRepository, pendingFamilyRequestStore);
    }

    @Test
    void subscribeDriverStatusShouldReturnSubscribeResponseOnSuccess() {
        var account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        var permission = Permission.of(Set.of("SUBSCRIBE_STATUS"));
        when(systemAccountRepository.findById(accountId.id())).thenReturn(Optional.of(account));
        when(permissionService.grantAccess(driverId.id(), accountId.id(), "SUBSCRIBE_STATUS"))
                .thenReturn(Result.ok(permission));

        var result = service.subscribeDriverStatus(accountId, driverId);

        assertTrue(result.isOk());
        assertEquals("SUBSCRIBED", result.unwrap().status());
        assertEquals(accountId.id(), result.unwrap().accountId());
        assertEquals(driverId.id(), result.unwrap().driverId());
        verify(driverStatusBroadcastService).broadcastStatus(driverId.id());
    }

    @Test
    void subscribeDriverStatusShouldReturnErrorWhenAccountNotFound() {
        when(systemAccountRepository.findById(accountId.id())).thenReturn(Optional.empty());

        var result = service.subscribeDriverStatus(accountId, driverId);

        assertTrue(result.isErr());
        assertTrue(result.unwrapErr().message().contains("not found"));
        verify(permissionService, never()).grantAccess(any(), any(), any());
    }

    @Test
    void subscribeDriverStatusShouldReturnErrorWhenAccountNotActive() {
        var account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        account.deactivate();
        when(systemAccountRepository.findById(accountId.id())).thenReturn(Optional.of(account));

        var result = service.subscribeDriverStatus(accountId, driverId);

        assertTrue(result.isErr());
        assertTrue(result.unwrapErr().message().contains("not active"));
    }

    @Test
    void subscribeDriverStatusShouldReturnErrorWhenGrantAccessFails() {
        var account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        var error = AppError.accessDenied("access denied");
        when(systemAccountRepository.findById(accountId.id())).thenReturn(Optional.of(account));
        when(permissionService.grantAccess(driverId.id(), accountId.id(), "SUBSCRIBE_STATUS"))
                .thenReturn(Result.err(error));

        var result = service.subscribeDriverStatus(accountId, driverId);

        assertTrue(result.isErr());
        assertEquals(error, result.unwrapErr());
    }

    @Test
    void unsubscribeDriverStatusShouldReturnOkOnSuccess() {
        when(permissionService.revokeAccess(driverId.id(), accountId.id(), "UNSUBSCRIBE_STATUS"))
                .thenReturn(Result.ok(null));

        var result = service.unsubscribeDriverStatus(accountId, driverId);

        assertTrue(result.isOk());
        assertNull(result.unwrap());
    }

    @Test
    void unsubscribeDriverStatusShouldReturnErrorWhenRevokeFails() {
        var error = AppError.accessDenied("revoke failed");
        when(permissionService.revokeAccess(driverId.id(), accountId.id(), "UNSUBSCRIBE_STATUS"))
                .thenReturn(Result.err(error));

        var result = service.unsubscribeDriverStatus(accountId, driverId);

        assertTrue(result.isErr());
        assertEquals(error, result.unwrapErr());
    }

    @Test
    void requestMediaSessionShouldReturnSessionResponseOnSuccess() throws Exception {
        var account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        var permission = Permission.of(Set.of("READ"));
        var handle = new MediaSessionPort.SessionHandle("session-1");
        when(systemAccountRepository.findById(accountId.id())).thenReturn(Optional.of(account));
        when(permissionService.checkPermission(driverId.id(), accountId.id()))
                .thenReturn(Result.ok(permission));
        when(mediaSessionPort.establishSession(any(AccountId.class), any(DriverId.class),
                eq(MediaSessionPort.SessionType.VIDEO))).thenReturn(handle);

        var result = service.requestMediaSession(accountId, driverId, "VIDEO");

        assertTrue(result.isOk());
        var resp = result.unwrap();
        assertEquals("session-1", resp.sessionHandle());
        assertEquals("VIDEO", resp.sessionType());
        assertEquals("ESTABLISHED", resp.status());
    }

    @Test
    void requestMediaSessionShouldReturnErrorWhenAccountNotFound() {
        when(systemAccountRepository.findById(accountId.id())).thenReturn(Optional.empty());

        var result = service.requestMediaSession(accountId, driverId, "AUDIO");

        assertTrue(result.isErr());
        assertTrue(result.unwrapErr().message().contains("not found"));
    }

    @Test
    void requestMediaSessionShouldReturnErrorWhenCheckPermissionFails() {
        var account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        var error = AppError.accessDenied("no permission record");
        when(systemAccountRepository.findById(accountId.id())).thenReturn(Optional.of(account));
        when(permissionService.checkPermission(driverId.id(), accountId.id()))
                .thenReturn(Result.err(error));

        var result = service.requestMediaSession(accountId, driverId, "AUDIO");

        assertTrue(result.isErr());
        assertEquals(error, result.unwrapErr());
    }

    @Test
    void requestMediaSessionShouldReturnErrorWhenPermissionRevoked() {
        var account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        when(systemAccountRepository.findById(accountId.id())).thenReturn(Optional.of(account));
        when(permissionService.checkPermission(driverId.id(), accountId.id()))
                .thenReturn(Result.ok(Permission.revoked()));

        var result = service.requestMediaSession(accountId, driverId, "AUDIO");

        assertTrue(result.isErr());
        assertTrue(result.unwrapErr().message().contains("does not have permission"));
    }

    @Test
    void requestMediaSessionShouldReturnErrorForInvalidSessionType() {
        var account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        var permission = Permission.of(Set.of("READ"));
        when(systemAccountRepository.findById(accountId.id())).thenReturn(Optional.of(account));
        when(permissionService.checkPermission(driverId.id(), accountId.id()))
                .thenReturn(Result.ok(permission));

        var result = service.requestMediaSession(accountId, driverId, "INVALID_TYPE");

        assertTrue(result.isErr());
        assertTrue(result.unwrapErr().message().contains("Invalid session type"));
    }

    @Test
    void requestMediaSessionShouldReturnErrorWhenEstablishSessionFails() throws Exception {
        var account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        var permission = Permission.of(Set.of("READ"));
        when(systemAccountRepository.findById(accountId.id())).thenReturn(Optional.of(account));
        when(permissionService.checkPermission(driverId.id(), accountId.id()))
                .thenReturn(Result.ok(permission));
        when(mediaSessionPort.establishSession(any(), any(), any()))
                .thenThrow(new MediaSessionPort.MediaSessionException.SessionEstablishFailedException("connection failed"));

        var result = service.requestMediaSession(accountId, driverId, "AUDIO");

        assertTrue(result.isErr());
        assertTrue(result.unwrapErr().message().contains("Media session establishment failed"));
    }

    @Test
    void endMediaSessionShouldReturnOkOnSuccess() throws Exception {
        var handle = new MediaSessionPort.SessionHandle("session-1");
        service.requestMediaSession(accountId, driverId, "AUDIO");
        var account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        var permission = Permission.of(Set.of("READ"));
        when(systemAccountRepository.findById(accountId.id())).thenReturn(Optional.of(account));
        when(permissionService.checkPermission(driverId.id(), accountId.id()))
                .thenReturn(Result.ok(permission));
        when(mediaSessionPort.establishSession(any(), any(), any())).thenReturn(handle);

        service.requestMediaSession(accountId, driverId, "AUDIO");

        var result = service.endMediaSession("session-1");

        assertTrue(result.isOk());
        verify(mediaSessionPort).terminateSession(handle);
    }

    @Test
    void endMediaSessionShouldReturnErrorWhenSessionNotFound() {
        var result = service.endMediaSession("nonexistent");

        assertTrue(result.isErr());
        assertTrue(result.unwrapErr().message().contains("not found"));
    }

    @Test
    void endMediaSessionShouldReturnErrorWhenTerminateSessionFails() throws Exception {
        var handle = new MediaSessionPort.SessionHandle("session-1");
        var account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        var permission = Permission.of(Set.of("READ"));
        when(systemAccountRepository.findById(accountId.id())).thenReturn(Optional.of(account));
        when(permissionService.checkPermission(driverId.id(), accountId.id()))
                .thenReturn(Result.ok(permission));
        when(mediaSessionPort.establishSession(any(), any(), any())).thenReturn(handle);

        service.requestMediaSession(accountId, driverId, "AUDIO");
        doThrow(new MediaSessionPort.MediaSessionException.SessionNotFoundException("session not found"))
                .when(mediaSessionPort).terminateSession(handle);

        var result = service.endMediaSession("session-1");

        assertTrue(result.isErr());
        assertTrue(result.unwrapErr().message().contains("termination failed"));
    }

    @Test
    void updateNotificationPreferenceShouldReturnOkWithValidRiskLevels() {
        var account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        when(systemAccountRepository.findById(accountId.id())).thenReturn(Optional.of(account));

        var result = service.updateNotificationPreference(accountId, driverId,
                List.of("L2_WARNING", "L3_CRITICAL"));

        assertTrue(result.isOk());
        verify(systemAccountRepository).save(account);
        assertTrue(account.notificationPreference().shouldNotify(RiskLevel.L2_WARNING));
    }

    @Test
    void updateNotificationPreferenceShouldDefaultToAllWhenNull() {
        var account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        when(systemAccountRepository.findById(accountId.id())).thenReturn(Optional.of(account));

        var result = service.updateNotificationPreference(accountId, driverId, null);

        assertTrue(result.isOk());
        verify(systemAccountRepository).save(account);
        assertTrue(account.notificationPreference().shouldNotify(RiskLevel.L1_HINT));
        assertTrue(account.notificationPreference().shouldNotify(RiskLevel.L2_WARNING));
        assertTrue(account.notificationPreference().shouldNotify(RiskLevel.L3_CRITICAL));
    }

    @Test
    void updateNotificationPreferenceShouldDefaultToAllWhenEmpty() {
        var account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        when(systemAccountRepository.findById(accountId.id())).thenReturn(Optional.of(account));

        var result = service.updateNotificationPreference(accountId, driverId, List.of());

        assertTrue(result.isOk());
        verify(systemAccountRepository).save(account);
    }

    @Test
    void updateNotificationPreferenceShouldReturnErrorWhenAccountNotFound() {
        when(systemAccountRepository.findById(accountId.id())).thenReturn(Optional.empty());

        var result = service.updateNotificationPreference(accountId, driverId, List.of("L2_WARNING"));

        assertTrue(result.isErr());
    }

    @Test
    void updateNotificationPreferenceShouldReturnErrorForInvalidRiskLevel() {
        var account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        when(systemAccountRepository.findById(accountId.id())).thenReturn(Optional.of(account));

        var result = service.updateNotificationPreference(accountId, driverId, List.of("INVALID_LEVEL"));

        assertTrue(result.isErr());
        assertTrue(result.unwrapErr().message().contains("Invalid risk level"));
    }

    @Test
    void triggerManualRescueShouldReturnRescueResponseOnSuccess() throws Exception {
        var account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        var permission = Permission.of(Set.of("RESCUE"));
        when(systemAccountRepository.findById(accountId.id())).thenReturn(Optional.of(account));
        when(permissionService.checkPermission(driverId.id(), accountId.id()))
                .thenReturn(Result.ok(permission));
        when(emergencyRescueService.triggerManualRescue(driverId, accountId))
                .thenReturn(Result.ok(null));

        var result = service.triggerManualRescue(accountId, driverId);

        assertTrue(result.isOk());
        assertEquals("TRIGGERED", result.unwrap().status());
        assertEquals("rescue-" + driverId.id(), result.unwrap().rescueReportId());
        verify(notificationPort).pushNotification(eq(accountId), any(NotificationPort.NotificationPayload.class));
    }

    @Test
    void triggerManualRescueShouldReturnErrorWhenAccountNotFound() {
        when(systemAccountRepository.findById(accountId.id())).thenReturn(Optional.empty());

        var result = service.triggerManualRescue(accountId, driverId);

        assertTrue(result.isErr());
    }

    @Test
    void triggerManualRescueShouldReturnErrorWhenCheckPermissionFails() {
        var account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        var error = AppError.accessDenied("no permission");
        when(systemAccountRepository.findById(accountId.id())).thenReturn(Optional.of(account));
        when(permissionService.checkPermission(driverId.id(), accountId.id()))
                .thenReturn(Result.err(error));

        var result = service.triggerManualRescue(accountId, driverId);

        assertTrue(result.isErr());
        assertEquals(error, result.unwrapErr());
    }

    @Test
    void triggerManualRescueShouldReturnErrorWhenPermissionRevoked() {
        var account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        when(systemAccountRepository.findById(accountId.id())).thenReturn(Optional.of(account));
        when(permissionService.checkPermission(driverId.id(), accountId.id()))
                .thenReturn(Result.ok(Permission.revoked()));

        var result = service.triggerManualRescue(accountId, driverId);

        assertTrue(result.isErr());
        assertTrue(result.unwrapErr().message().contains("does not have guardianship permission"));
    }

    @Test
    void triggerManualRescueShouldReturnErrorWhenRescueFails() {
        var account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        var permission = Permission.of(Set.of("RESCUE"));
        var error = AppError.invalidState("rescue already in progress");
        when(systemAccountRepository.findById(accountId.id())).thenReturn(Optional.of(account));
        when(permissionService.checkPermission(driverId.id(), accountId.id()))
                .thenReturn(Result.ok(permission));
        when(emergencyRescueService.triggerManualRescue(driverId, accountId))
                .thenReturn(Result.err(error));

        var result = service.triggerManualRescue(accountId, driverId);

        assertTrue(result.isErr());
        assertEquals(error, result.unwrapErr());
    }

    @Test
    void triggerManualRescueShouldSucceedEvenWhenNotificationFails() throws Exception {
        var account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        var permission = Permission.of(Set.of("RESCUE"));
        when(systemAccountRepository.findById(accountId.id())).thenReturn(Optional.of(account));
        when(permissionService.checkPermission(driverId.id(), accountId.id()))
                .thenReturn(Result.ok(permission));
        when(emergencyRescueService.triggerManualRescue(driverId, accountId))
                .thenReturn(Result.ok(null));
        doThrow(new NotificationPort.NotificationException.DeliveryFailedException("delivery failed"))
                .when(notificationPort).pushNotification(any(), any());

        var result = service.triggerManualRescue(accountId, driverId);

        assertTrue(result.isOk());
        assertEquals("TRIGGERED", result.unwrap().status());
    }

    @Test
    void queryGuardianshipPermissionsShouldReturnPermissionsOnSuccess() {
        var account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        var permission = Permission.of(Set.of("READ", "SUBSCRIBE_STATUS"));
        when(systemAccountRepository.findById(accountId.id())).thenReturn(Optional.of(account));
        when(permissionService.checkPermission(driverId.id(), accountId.id()))
                .thenReturn(Result.ok(permission));

        var result = service.queryGuardianshipPermissions(accountId, driverId);

        assertTrue(result.isOk());
        var resp = result.unwrap();
        assertEquals(accountId.id(), resp.accountId());
        assertEquals(driverId.id(), resp.driverId());
        assertFalse(resp.isRevoked());
        assertTrue(resp.permissions().contains("READ"));
    }

    @Test
    void queryGuardianshipPermissionsShouldReturnRevokedTrue() {
        var account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        when(systemAccountRepository.findById(accountId.id())).thenReturn(Optional.of(account));
        when(permissionService.checkPermission(driverId.id(), accountId.id()))
                .thenReturn(Result.ok(Permission.revoked()));

        var result = service.queryGuardianshipPermissions(accountId, driverId);

        assertTrue(result.isOk());
        assertTrue(result.unwrap().isRevoked());
        assertTrue(result.unwrap().permissions().isEmpty());
    }

    @Test
    void queryGuardianshipPermissionsShouldReturnErrorWhenAccountNotFound() {
        when(systemAccountRepository.findById(accountId.id())).thenReturn(Optional.empty());

        var result = service.queryGuardianshipPermissions(accountId, driverId);

        assertTrue(result.isErr());
    }

    @Test
    void queryGuardianshipPermissionsShouldReturnErrorWhenCheckPermissionFails() {
        var account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        var error = AppError.notFound("Permission", "drv-1");
        when(systemAccountRepository.findById(accountId.id())).thenReturn(Optional.of(account));
        when(permissionService.checkPermission(driverId.id(), accountId.id()))
                .thenReturn(Result.err(error));

        var result = service.queryGuardianshipPermissions(accountId, driverId);

        assertTrue(result.isErr());
        assertEquals(error, result.unwrapErr());
    }

    @Test
    void controlVehicleWindowShouldReturnResponseOnSuccess() {
        var account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        when(systemAccountRepository.findById(accountId.id())).thenReturn(Optional.of(account));

        var result = service.controlVehicleWindow(accountId, driverId, "OPEN");

        assertTrue(result.isOk());
        assertEquals("CONTROLLED", result.unwrap().status());
        assertEquals("OPEN", result.unwrap().windowPosition());
    }

    @Test
    void controlVehicleWindowShouldReturnErrorWhenAccountNotFound() {
        when(systemAccountRepository.findById(accountId.id())).thenReturn(Optional.empty());

        var result = service.controlVehicleWindow(accountId, driverId, "CLOSE");

        assertTrue(result.isErr());
    }

    @Test
    void queryWindowStatusShouldReturnResponseOnSuccess() {
        var account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        when(systemAccountRepository.findById(accountId.id())).thenReturn(Optional.of(account));

        var result = service.queryWindowStatus(accountId, driverId);

        assertTrue(result.isOk());
        assertEquals("CLOSED", result.unwrap().windowPosition());
        assertTrue(result.unwrap().isClosed());
    }

    @Test
    void queryWindowStatusShouldReturnErrorWhenAccountNotFound() {
        when(systemAccountRepository.findById(accountId.id())).thenReturn(Optional.empty());

        var result = service.queryWindowStatus(accountId, driverId);

        assertTrue(result.isErr());
    }

    @Test
    void issueSparkRTCTokenShouldReturnTokenResponseOnSuccess() {
        var account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        when(systemAccountRepository.findById(accountId.id())).thenReturn(Optional.of(account));

        var result = service.issueSparkRTCToken(accountId, driverId, "channel-1");

        assertTrue(result.isOk());
        var resp = result.unwrap();
        assertNotNull(resp.token());
        assertEquals("channel-1", resp.channelName());
        assertTrue(resp.expireTime() > 0);
    }

    @Test
    void issueSparkRTCTokenShouldReturnErrorWhenAccountNotFound() {
        when(systemAccountRepository.findById(accountId.id())).thenReturn(Optional.empty());

        var result = service.issueSparkRTCToken(accountId, driverId, "channel-1");

        assertTrue(result.isErr());
    }
}
