package com.aiot.infra.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock 二次身份验证提供者。
 * <p>
 * 本期为课程作业打桩实现：硬编码验证码 {@code 123456}，
 * 在控制台打印验证码发送提示，不调用真实的短信/生物特征验证服务。
 * </p>
 * <p>
 * 设计依据：docs/ood_infrastructure.md §3.6.3
 * </p>
 */
@Component
public class MockSecondaryAuthProvider implements SecondaryAuthProvider {

    private static final Logger log = LoggerFactory.getLogger(MockSecondaryAuthProvider.class);

    /**
     * 高危失能场景——豁免二次验证。
     */
    private static final Set<String> EXEMPTION_SCENARIOS = Set.of(
            "EMERGENCY_ACTIVATED",
            "SOS_AUTO_TRIGGERED"
    );

    private final SecurityProperties properties;

    /**
     * 账户 → (验证码 → 过期时间)
     */
    private final ConcurrentHashMap<String, CodeEntry> codeStore = new ConcurrentHashMap<>();

    public MockSecondaryAuthProvider(SecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean verify(String accountId, String token, String operation) {
        log.info("二次身份验证请求: accountId={}, operation={}, token={}", accountId, operation, token);

        // 检查本地存储的验证码
        CodeEntry entry = codeStore.get(accountId);
        if (entry == null) {
            log.warn("二次验证失败: accountId={}, 未找到验证码记录", accountId);
            return false;
        }

        // 检查过期
        if (entry.expiresAt.isBefore(Instant.now())) {
            log.warn("二次验证失败: accountId={}, 验证码已过期", accountId);
            codeStore.remove(accountId);
            return false;
        }

        // 比较验证码
        if (entry.code.equals(token)) {
            log.info("二次验证通过: accountId={}, operation={}", accountId, operation);
            codeStore.remove(accountId); // 一次性使用
            return true;
        }

        log.warn("二次验证失败: accountId={}, 验证码不匹配", accountId);
        return false;
    }

    @Override
    public boolean requiresSecondaryAuth(String operation, String scenario) {
        if (EXEMPTION_SCENARIOS.contains(scenario)) {
            log.info("高危失能场景豁免二次验证: scenario={}, operation={}", scenario, operation);
            return false;
        }
        return true;
    }

    @Override
    public String sendVerificationCode(String accountId) {
        String code = properties.getMockVerificationCode();
        log.info("=== [Mock] 二次验证码已生成: accountId={}, code={}, 有效期={}秒 ===",
                accountId, code, properties.getMockCodeValiditySeconds());

        // 存储验证码
        Instant expiresAt = Instant.now().plusSeconds(properties.getMockCodeValiditySeconds());
        codeStore.put(accountId, new CodeEntry(code, expiresAt));

        return code;
    }

    private record CodeEntry(String code, Instant expiresAt) {}
}
