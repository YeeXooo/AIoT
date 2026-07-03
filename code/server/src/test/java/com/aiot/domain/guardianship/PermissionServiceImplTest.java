package com.aiot.domain.guardianship;

import com.aiot.domain.event.AccessGrantReason;
import com.aiot.domain.event.DomainEventPublisher;
import com.aiot.domain.event.FamilyAccessGrantedEvent;
import com.aiot.domain.event.FamilyAccessRevokedEvent;
import com.aiot.domain.model.AccountRole;
import com.aiot.domain.model.L3DurationTracker;
import com.aiot.domain.model.Permission;
import com.aiot.domain.model.SystemAccount;
import com.aiot.domain.repository.SystemAccountRepository;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PermissionServiceImplTest {

    @Mock private DomainEventPublisher eventPublisher;
    @Mock private SystemAccountRepository accountRepository;

    private PermissionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PermissionServiceImpl(eventPublisher, accountRepository);
    }

    @Test
    void grantAccessGrantsPermissionForExistingAccount() {
        SystemAccount account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        when(accountRepository.findById(account.accountId().id())).thenReturn(Optional.of(account));

        Result<Permission, AppError> result = service.grantAccess("d1", account.accountId().id(), "NORMAL");

        assertTrue(result.isOk());
        assertFalse(result.unwrap().isRevoked());
        assertTrue(result.unwrap().getOperations().contains("REMOTE_VIDEO"));
        verify(accountRepository).save(account);
        verify(eventPublisher).publish(any(FamilyAccessGrantedEvent.class));
    }

    @Test
    void grantAccessReturnsErrorForMissingAccount() {
        when(accountRepository.findById("nonexistent")).thenReturn(Optional.empty());

        Result<Permission, AppError> result = service.grantAccess("d1", "nonexistent", "NORMAL");

        assertTrue(result.isErr());
        assertEquals("NotFound", result.unwrapErr().code());
    }

    @Test
    void grantAccessResolvesEmergencyReason() {
        SystemAccount account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        when(accountRepository.findById(account.accountId().id())).thenReturn(Optional.of(account));

        Result<Permission, AppError> result = service.grantAccess("d1", account.accountId().id(), "EMERGENCY");

        assertTrue(result.isOk());
        verify(eventPublisher).publish(any(FamilyAccessGrantedEvent.class));
    }

    @Test
    void grantAccessResolvesOcclusionRestoredReason() {
        SystemAccount account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        when(accountRepository.findById(account.accountId().id())).thenReturn(Optional.of(account));

        Result<Permission, AppError> result = service.grantAccess("d1", account.accountId().id(), "OCCLUSION_RESTORED");

        assertTrue(result.isOk());
        verify(eventPublisher).publish(any(FamilyAccessGrantedEvent.class));
    }

    @Test
    void grantAccessDefaultsToNormalForUnknownReason() {
        SystemAccount account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        when(accountRepository.findById(account.accountId().id())).thenReturn(Optional.of(account));

        Result<Permission, AppError> result = service.grantAccess("d1", account.accountId().id(), "SOMETHING_ELSE");

        assertTrue(result.isOk());
    }

    @Test
    void grantAccessDefaultsToNormalForNullReason() {
        SystemAccount account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        when(accountRepository.findById(account.accountId().id())).thenReturn(Optional.of(account));

        Result<Permission, AppError> result = service.grantAccess("d1", account.accountId().id(), null);

        assertTrue(result.isOk());
    }

    @Test
    void revokeAccessRevokesPermissionForExistingAccount() {
        SystemAccount account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        when(accountRepository.findById(account.accountId().id())).thenReturn(Optional.of(account));

        Result<Void, AppError> result = service.revokeAccess("d1", account.accountId().id(), "RISK_DECREASED");

        assertTrue(result.isOk());
        assertTrue(account.permission().isRevoked());
        verify(accountRepository).save(account);
        verify(eventPublisher).publish(any(FamilyAccessRevokedEvent.class));
    }

    @Test
    void revokeAccessReturnsErrorForMissingAccount() {
        when(accountRepository.findById("nonexistent")).thenReturn(Optional.empty());

        Result<Void, AppError> result = service.revokeAccess("d1", "nonexistent", "RISK_DECREASED");

        assertTrue(result.isErr());
        assertEquals("NotFound", result.unwrapErr().code());
    }

    @Test
    void revokeAccessResolvesPhysicalOcclusionReason() {
        SystemAccount account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        when(accountRepository.findById(account.accountId().id())).thenReturn(Optional.of(account));

        Result<Void, AppError> result = service.revokeAccess("d1", account.accountId().id(), "PHYSICAL_OCCLUSION");

        assertTrue(result.isOk());
    }

    @Test
    void revokeAccessResolvesDriverDeactivatedReason() {
        SystemAccount account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        when(accountRepository.findById(account.accountId().id())).thenReturn(Optional.of(account));

        Result<Void, AppError> result = service.revokeAccess("d1", account.accountId().id(), "DRIVER_DEACTIVATED");

        assertTrue(result.isOk());
    }

    @Test
    void revokeAccessDefaultsToRiskDecreasedForNullReason() {
        SystemAccount account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        when(accountRepository.findById(account.accountId().id())).thenReturn(Optional.of(account));

        Result<Void, AppError> result = service.revokeAccess("d1", account.accountId().id(), null);

        assertTrue(result.isOk());
    }

    @Test
    void checkPermissionReturnsPermissionForAuthorizedAccount() {
        SystemAccount account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        account.updatePermission(Permission.of(java.util.Set.of("REMOTE_VIDEO")));
        when(accountRepository.findById(account.accountId().id())).thenReturn(Optional.of(account));

        Result<Permission, AppError> result = service.checkPermission("d1", account.accountId().id());

        assertTrue(result.isOk());
        assertTrue(result.unwrap().getOperations().contains("REMOTE_VIDEO"));
    }

    @Test
    void checkPermissionReturnsErrorForRevokedPermission() {
        SystemAccount account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        when(accountRepository.findById(account.accountId().id())).thenReturn(Optional.of(account));

        Result<Permission, AppError> result = service.checkPermission("d1", account.accountId().id());

        assertTrue(result.isErr());
        assertEquals("AccessDenied", result.unwrapErr().code());
    }

    @Test
    void checkPermissionReturnsErrorForMissingAccount() {
        when(accountRepository.findById("nonexistent")).thenReturn(Optional.empty());

        Result<Permission, AppError> result = service.checkPermission("d1", "nonexistent");

        assertTrue(result.isErr());
        assertEquals("NotFound", result.unwrapErr().code());
    }

    @Test
    void onL3DurationReachedGrantsPermissionWhenThresholdExceeded() {
        SystemAccount account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        when(accountRepository.findAll()).thenReturn(List.of(account));
        L3DurationTracker tracker = L3DurationTracker.start(Instant.now().minusSeconds(120)).advance(Instant.now());

        service.onL3DurationReached("d1", tracker);

        verify(accountRepository).save(account);
        verify(eventPublisher).publish(any(FamilyAccessGrantedEvent.class));
    }

    @Test
    void onL3DurationReachedDoesNothingWhenBelowThreshold() {
        L3DurationTracker tracker = L3DurationTracker.start(Instant.now());

        service.onL3DurationReached("d1", tracker);

        verify(accountRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void onL3DurationReachedSkipsNonRevokedAccounts() {
        SystemAccount account = SystemAccount.create("13800000001", AccountRole.FAMILY);
        account.updatePermission(Permission.of(java.util.Set.of("REMOTE_VIDEO")));
        when(accountRepository.findAll()).thenReturn(List.of(account));
        L3DurationTracker tracker = L3DurationTracker.start(Instant.now().minusSeconds(120)).advance(Instant.now());

        service.onL3DurationReached("d1", tracker);

        verify(accountRepository, never()).save(account);
    }
}
