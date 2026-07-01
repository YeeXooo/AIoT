package com.aiot.domain.model;

import com.aiot.domain.model.exception.BusinessException;
import com.aiot.domain.shared.VehicleId;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;

/**
 * 救援授权凭证（VO-23）
 * 救援机构高敏操作的授权凭证，支持签发-校验-消费-过期全生命周期
 */
@Embeddable
@Getter
@EqualsAndHashCode
public final class RescueAuthorizationToken {
    private final String tokenId;
    private final String issuer;
    private final VehicleId targetVehicleId;
    private final Set<String> authorizedOperations;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private final boolean consumed;

    private RescueAuthorizationToken(String tokenId, String issuer, VehicleId targetVehicleId,
                                     Set<String> operations, Instant issuedAt,
                                     Instant expiresAt, boolean consumed) {
        if (tokenId == null || tokenId.isBlank()) {
            throw new BusinessException(
                    "MODEL_047",
                    "救援授权凭证标识不能为空",
                    "RESCUE_AUTH_TOKEN_VALIDATE"
            );
        }
        if (issuedAt == null || expiresAt == null) {
            throw new BusinessException(
                    "MODEL_048",
                    "授权签发时间与过期时间不能为空",
                    "RESCUE_AUTH_TOKEN_VALIDATE"
            );
        }
        if (expiresAt.isBefore(issuedAt)) {
            throw new BusinessException(
                    "MODEL_049",
                    "授权过期时间不能早于签发时间",
                    "RESCUE_AUTH_TOKEN_VALIDATE"
            );
        }
        this.tokenId = tokenId;
        this.issuer = issuer;
        this.targetVehicleId = targetVehicleId;
        this.authorizedOperations = operations == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(operations);
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.consumed = consumed;
    }

    public static RescueAuthorizationToken issue(String tokenId, String issuer, VehicleId target,
                                                 Set<String> ops, Instant issuedAt, Instant expiresAt) {
        return new RescueAuthorizationToken(tokenId, issuer, target, ops, issuedAt, expiresAt, false);
    }

    public boolean isValid(Instant now) {
        return !consumed && now.isAfter(issuedAt) && now.isBefore(expiresAt);
    }

    public RescueAuthorizationToken consume() {
        return new RescueAuthorizationToken(tokenId, issuer, targetVehicleId,
                authorizedOperations, issuedAt, expiresAt, true);
    }

    protected RescueAuthorizationToken() {
        this.tokenId = "";
        this.issuer = "";
        this.targetVehicleId = null;
        this.authorizedOperations = Collections.emptySet();
        this.issuedAt = Instant.EPOCH;
        this.expiresAt = Instant.EPOCH;
        this.consumed = false;
    }
<<<<<<< HEAD
=======

>>>>>>> d61a4a60204c7e68e9b5b3ec725a630abc2e642a
}