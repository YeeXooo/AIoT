package com.aiot.infra.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "t_guardianship")
@IdClass(GuardianshipEntity.GuardianshipId.class)
public class GuardianshipEntity {

    @Id
    @Column(name = "driver_id", length = 36)
    private String driverId;

    @Id
    @Column(name = "account_id", length = 36)
    private String accountId;

    @Id
    @Column(name = "granted_at")
    private LocalDateTime grantedAt;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String permissions;

    @Column(nullable = false, length = 32)
    private String grantReason;

    private LocalDateTime revokedAt;

    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public LocalDateTime getGrantedAt() { return grantedAt; }
    public void setGrantedAt(LocalDateTime grantedAt) { this.grantedAt = grantedAt; }
    public String getPermissions() { return permissions; }
    public void setPermissions(String permissions) { this.permissions = permissions; }
    public String getGrantReason() { return grantReason; }
    public void setGrantReason(String grantReason) { this.grantReason = grantReason; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime revokedAt) { this.revokedAt = revokedAt; }

    public static class GuardianshipId implements java.io.Serializable {
        private String driverId;
        private String accountId;
        private LocalDateTime grantedAt;

        public GuardianshipId() { }
        public GuardianshipId(String driverId, String accountId, LocalDateTime grantedAt) {
            this.driverId = driverId; this.accountId = accountId; this.grantedAt = grantedAt;
        }
        public String getDriverId() { return driverId; }
        public void setDriverId(String driverId) { this.driverId = driverId; }
        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }
        public LocalDateTime getGrantedAt() { return grantedAt; }
        public void setGrantedAt(LocalDateTime grantedAt) { this.grantedAt = grantedAt; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof GuardianshipId)) { return false; }
            GuardianshipId that = (GuardianshipId) o;
            return driverId.equals(that.driverId) && accountId.equals(that.accountId) && grantedAt.equals(that.grantedAt);
        }
        @Override public int hashCode() { return driverId.hashCode() + accountId.hashCode() + grantedAt.hashCode(); }
    }
}
