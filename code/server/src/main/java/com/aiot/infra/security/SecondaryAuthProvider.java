package com.aiot.infra.security;

/**
 * 二次身份验证提供者接口。
 * <p>
 * 为高敏操作（远程控车、音视频对讲、救援授权等）提供
 * 二次身份验证门控能力，供领域层 PermissionService 调用。
 * </p>
 * <p>
 * 设计依据：docs/ood_infrastructure.md §3.6.3
 * </p>
 */
public interface SecondaryAuthProvider {

    /**
     * 校验二次身份验证凭证。
     *
     * @param accountId 账户标识
     * @param token     验证凭证（OTP 验证码 / 生物特征 token）
     * @param operation 待执行的高敏操作名称
     * @return 验证是否通过
     */
    boolean verify(String accountId, String token, String operation);

    /**
     * 判断指定操作在当前场景下是否需要二次身份验证。
     * <p>
     * 高危失能场景（EMERGENCY_ACTIVATED）可豁免二次验证。
     * </p>
     *
     * @param operation 待执行的高敏操作名称
     * @param scenario  当前场景标识
     * @return 是否需要二次身份验证
     */
    boolean requiresSecondaryAuth(String operation, String scenario);

    /**
     * 生成并发送二次身份验证码。
     * <p>
     * 本期 mock 实现：在控制台打印验证码。
     * </p>
     *
     * @param accountId 账户标识
     * @return 生成的验证码（供验证阶段使用）
     */
    String sendVerificationCode(String accountId);
}
