package com.aiot.domain.model;

import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import java.util.Collections;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 访问权限（VO-07）
 * 家属账户对驾驶员的可操作集合，不可变；撤销以空集合替换
 */
@Embeddable
@Getter
@EqualsAndHashCode
public final class Permission {
    private final Set<String> operations;

    private Permission(Set<String> operations) {
        this.operations = operations == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(operations);
    }

    public static Permission of(Set<String> operations) {
        return new Permission(operations);
    }

    public static Permission revoked() {
        return new Permission(Collections.emptySet());
    }

    public boolean isRevoked() {
        return operations.isEmpty();
    }

    protected Permission() {
        this.operations = Collections.emptySet();
    }
}