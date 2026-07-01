package com.aiot.domain.guardianship;

import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.AccountId;
import com.aiot.domain.event.DomainEventPublisher;
import com.aiot.domain.event.FamilyAccessGrantedEvent;
import com.aiot.domain.event.FamilyAccessRevokedEvent;
import com.aiot.domain.port.NotificationPort;
import com.aiot.domain.port.NotificationPort.NotificationPayload;
import com.aiot.domain.port.NotificationPort.NotificationType;
import com.aiot.domain.port.NotificationPort.NotificationPriority;
import com.aiot.domain.model.Driver;
import com.aiot.domain.model.SystemAccount;
import com.aiot.domain.repository.DriverRepository;
import com.aiot.domain.repository.SystemAccountRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class PermissionService {

    private final DriverRepository driverRepo;
    private final SystemAccountRepository accountRepo;
    private final NotificationPort notificationPort;
    private final DomainEventPublisher eventPublisher;

    public PermissionService(DriverRepository driverRepo,
                              SystemAccountRepository accountRepo,
                              NotificationPort notificationPort,
                              DomainEventPublisher eventPublisher) {
        this.driverRepo = driverRepo;
        this.accountRepo = accountRepo;
        this.notificationPort = notificationPort;
        this.eventPublisher = eventPublisher;
    }

    public Result<Void, AppError> grantAccess(DriverId driverId, AccountId accountId) {
        Optional<Driver> driverOpt = driverRepo.findById(driverId);
        if (driverOpt.isEmpty()) return Result.err(AppError.notFound("Driver not found"));

        Optional<SystemAccount> accountOpt = accountRepo.findById(accountId);
        if (accountOpt.isEmpty()) return Result.err(AppError.notFound("Account not found"));

        FamilyAccessGrantedEvent event = new FamilyAccessGrantedEvent(
            driverId, accountId, Instant.now());
        eventPublisher.publish(event);

        try {
            NotificationPayload payload = new NotificationPayload(
                NotificationType.STATUS_SNAPSHOT, "Access Granted",
                "Family access granted for driver " + driverId.id(),
                NotificationPriority.NORMAL);
            notificationPort.pushNotification(accountId, payload);
        } catch (Exception e) {
            // non-critical — continue
        }

        return Result.ok(null);
    }

    public Result<Void, AppError> revokeAccess(DriverId driverId, AccountId accountId) {
        FamilyAccessRevokedEvent event = new FamilyAccessRevokedEvent(
            driverId, accountId, Instant.now());
        eventPublisher.publish(event);
        return Result.ok(null);
    }
}
