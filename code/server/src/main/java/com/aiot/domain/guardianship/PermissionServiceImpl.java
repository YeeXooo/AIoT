package com.aiot.domain.guardianship;

import com.aiot.domain.event.AccessGrantReason;
import com.aiot.domain.event.AccessRevocationReason;
import com.aiot.domain.event.DomainEventPublisher;
import com.aiot.domain.event.FamilyAccessGrantedEvent;
import com.aiot.domain.event.FamilyAccessRevokedEvent;
import com.aiot.domain.model.L3DurationTracker;
import com.aiot.domain.model.Permission;
import com.aiot.domain.model.SystemAccount;
import com.aiot.domain.repository.SystemAccountRepository;
import com.aiot.domain.shared.AccountId;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.Result;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class PermissionServiceImpl implements PermissionService {

    private static final Duration L3_DURATION_THRESHOLD = Duration.ofSeconds(60);
    private static final Set<String> DEFAULT_PERMISSIONS = Set.of("REMOTE_VIDEO", "REMOTE_VOICE", "LOCATION_TRACK");

    private final DomainEventPublisher eventPublisher;
    private final SystemAccountRepository accountRepository;

    public PermissionServiceImpl(DomainEventPublisher eventPublisher,
                                  SystemAccountRepository accountRepository) {
        this.eventPublisher = eventPublisher;
        this.accountRepository = accountRepository;
    }

    @Override
    public Result<Permission, AppError> grantAccess(String driverId, String accountId, String reason) {
        return accountRepository.findById(accountId)
                .map(account -> {
                    Permission permission = Permission.of(DEFAULT_PERMISSIONS);
                    account.updatePermission(permission);
                    accountRepository.save(account);

                    eventPublisher.publish(new FamilyAccessGrantedEvent(
                            new AccountId(accountId),
                            new DriverId(driverId),
                            resolveGrantReason(reason),
                            List.copyOf(DEFAULT_PERMISSIONS),
                            Instant.now()
                    ));
                    return Result.<Permission, AppError>ok(permission);
                })
                .orElseGet(() -> Result.err(AppError.notFound("SystemAccount", accountId)));
    }

    @Override
    public Result<Void, AppError> revokeAccess(String driverId, String accountId, String reason) {
        return accountRepository.findById(accountId)
                .map(account -> {
                    account.updatePermission(Permission.revoked());
                    accountRepository.save(account);

                    eventPublisher.publish(new FamilyAccessRevokedEvent(
                            new AccountId(accountId),
                            new DriverId(driverId),
                            resolveRevocationReason(reason),
                            Instant.now()
                    ));
                    return Result.<Void, AppError>ok(null);
                })
                .orElseGet(() -> Result.err(AppError.notFound("SystemAccount", accountId)));
    }

    @Override
    public Result<Permission, AppError> checkPermission(String driverId, String accountId) {
        return accountRepository.findById(accountId)
                .map(account -> {
                    Permission permission = account.permission();
                    if (permission.isRevoked()) {
                        return Result.<Permission, AppError>err(
                                AppError.accessDenied("No permission granted for driver " + driverId));
                    }
                    return Result.<Permission, AppError>ok(permission);
                })
                .orElseGet(() -> Result.err(AppError.notFound("SystemAccount", accountId)));
    }

    @Override
    public void onL3DurationReached(String driverId, L3DurationTracker tracker) {
        if (tracker.getAccumulatedDuration().compareTo(L3_DURATION_THRESHOLD) >= 0) {
            // TODO: accountRepository.findByDriver(driverId) not available, use findAll+filter
            List<SystemAccount> accounts = accountRepository.findAll();
            for (SystemAccount account : accounts) {
                if (account.permission().isRevoked()) {
                    Permission permission = Permission.of(DEFAULT_PERMISSIONS);
                    account.updatePermission(permission);
                    accountRepository.save(account);

                    eventPublisher.publish(new FamilyAccessGrantedEvent(
                            account.accountId(),
                            new DriverId(driverId),
                            AccessGrantReason.NORMAL,
                            List.copyOf(DEFAULT_PERMISSIONS),
                            Instant.now()
                    ));
                }
            }
        }
    }

    private AccessGrantReason resolveGrantReason(String reason) {
        if (reason == null) {
            return AccessGrantReason.NORMAL;
        }
        return switch (reason.toUpperCase()) {
            case "EMERGENCY" -> AccessGrantReason.EMERGENCY;
            case "OCCLUSION_RESTORED" -> AccessGrantReason.OCCLUSION_RESTORED;
            default -> AccessGrantReason.NORMAL;
        };
    }

    private AccessRevocationReason resolveRevocationReason(String reason) {
        if (reason == null) {
            return AccessRevocationReason.RISK_DECREASED;
        }
        return switch (reason.toUpperCase()) {
            case "PHYSICAL_OCCLUSION" -> AccessRevocationReason.PHYSICAL_OCCLUSION;
            case "DRIVER_DEACTIVATED" -> AccessRevocationReason.DRIVER_DEACTIVATED;
            default -> AccessRevocationReason.RISK_DECREASED;
        };
    }
}
