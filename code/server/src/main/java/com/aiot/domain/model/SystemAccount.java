package com.aiot.domain.model;

import com.aiot.domain.shared.AccountId;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

public class SystemAccount {

    private final AccountId accountId;
    private String phone;
    private String email;
    private AccountRole role;
    private NotificationPreference notificationPreference;
    private Permission permission;
    private Integer version;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean active;

    private SystemAccount(AccountId accountId, String phone, AccountRole role) {
        this.accountId = accountId;
        this.phone = phone;
        this.email = null;
        this.role = role;
        this.notificationPreference = NotificationPreference.defaultPreference();
        this.permission = Permission.none();
        this.version = 1;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.active = true;
    }

    public static SystemAccount create(String phone, AccountRole role) {
        Objects.requireNonNull(phone, "phone must not be null");
        Objects.requireNonNull(role, "role must not be null");
        if (phone.isBlank()) {
            throw new IllegalArgumentException("phone must not be blank");
        }

        return new SystemAccount(AccountId.generate(), phone, role);
    }

    public void updatePhone(String phone) {
        Objects.requireNonNull(phone, "phone must not be null");
        if (phone.isBlank()) {
            throw new IllegalArgumentException("phone must not be blank");
        }
        this.phone = phone;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateEmail(String email) {
        this.email = email;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateRole(AccountRole role) {
        Objects.requireNonNull(role, "role must not be null");
        this.role = role;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateNotificationPreference(NotificationPreference preference) {
        Objects.requireNonNull(preference, "preference must not be null");
        this.notificationPreference = preference;
        this.updatedAt = LocalDateTime.now();
    }

    public void updatePermission(Permission permission) {
        Objects.requireNonNull(permission, "permission must not be null");
        this.permission = permission;
        this.updatedAt = LocalDateTime.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = LocalDateTime.now();
    }

    public void activate() {
        this.active = true;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean canReceiveAlert(RiskLevel riskLevel) {
        Objects.requireNonNull(riskLevel, "riskLevel must not be null");
        return notificationPreference.shouldNotify(riskLevel);
    }

    public boolean hasPermission(Permission.PermissionType type) {
        Objects.requireNonNull(type, "type must not be null");
        return permission.hasPermission(type);
    }

    public void validate() {
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(phone, "phone must not be null");
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(notificationPreference, "notificationPreference must not be null");
        Objects.requireNonNull(permission, "permission must not be null");
        if (phone.isBlank()) {
            throw new IllegalStateException("phone must not be blank");
        }
    }

    public AccountId accountId() { return accountId; }
    public String phone() { return phone; }
    public Optional<String> email() { return Optional.ofNullable(email); }
    public AccountRole role() { return role; }
    public NotificationPreference notificationPreference() { return notificationPreference; }
    public Permission permission() { return permission; }
    public Integer version() { return version; }
    public void version(Integer version) { this.version = version; }
    public LocalDateTime createdAt() { return createdAt; }
    public LocalDateTime updatedAt() { return updatedAt; }
    public boolean isActive() { return active; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SystemAccount that = (SystemAccount) o;
        return Objects.equals(accountId, that.accountId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(accountId);
    }
}