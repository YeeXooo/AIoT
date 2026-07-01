package com.aiot.infra.security;

/**
 * 二次身份验证提供者接口。
 * <p>
 * 为 PermissionService 的二次身份验证门控提供抽象。
 * 高敏操作（远程对讲、视频监控、远程车窗控制）前须完成二次验证。
 * </p>
 * <p>
 * 设计依据：docs/ood_infrastructure.md §3.6.3
 * </p>
 */
public interface SecondaryAuthProvider {

    /**
     * 验证二次身份。
     *
     * @param credential 验证凭据（Token 或验证码）
     * @param operation  操作类型
     * @return 验证结果
     */
    AuthResult verify(String credential, String operation);

    /**
     * 检查操作是否需要二次验证。
     *
     * @param operation 操作类型
     * @param scenario  场景类型
     * @return 如果需要二次验证则返回 true
     */
    boolean requiresSecondaryAuth(String operation, String scenario);

    /**
     * 验证结果。
     *
     * @param isSuccess 是否成功
     * @param message   结果消息
     */
    record AuthResult(boolean isSuccess, String message) {

        public static AuthResult success() {
            return new AuthResult(true, "Authentication successful");
        }

        public static AuthResult failure(String message) {
            return new AuthResult(false, message);
        }
    }
}
