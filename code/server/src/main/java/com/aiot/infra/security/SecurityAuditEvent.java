package com.aiot.infra.security;

import java.time.Instant;

/**
 * 安全审计事件。
 * <p>
 * 用于记录脱敏校验失败、未授权访问等安全违规行为。
 * </p>
 * <p>
 * 设计依据：docs/ood_infrastructure.md §3.6.1
 * </p>
 */
public record SecurityAuditEvent(
        Instant timestamp,
        String source,
        String violation,
        String detail
) {

    public static SecurityAuditEvent create(String source, String violation, String detail) {
        return new SecurityAuditEvent(Instant.now(), source, violation, detail);
    }
}
