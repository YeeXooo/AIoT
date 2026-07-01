package com.aiot.infra.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Mock 二次身份验证提供者。
 * <p>
 * 硬编码验证码 123456，控制台打印即可。
 * 高危失能场景（EMERGENCY_ACTIVATED）豁免二次验证。
 * </p>
 * <p>
 * 设计依据：docs/ood_infrastructure.md §3.6.3
 * </p>
 */
@Component
public class MockSecondaryAuthProvider implements SecondaryAuthProvider {

    private static final Logger log = LoggerFactory.getLogger(MockSecondaryAuthProvider.class);

    /**
     * 硬编码验证码。
     */
    private static final String MOCK_VERIFICATION_CODE = "123456";

    /**
     * 高危失能场景标识。
     */
    private static final String EMERGENCY_ACTIVATED = "EMERGENCY_ACTIVATED";

    @Override
    public AuthResult verify(String credential, String operation) {
        log.info("Secondary auth verification: operation={}, credential={}", operation, credential);

        if (MOCK_VERIFICATION_CODE.equals(credential)) {
            log.info("Secondary auth passed: operation={}", operation);
            return AuthResult.success();
        }

        log.warn("Secondary auth failed: operation={}, reason=invalid credential", operation);
        return AuthResult.failure("Invalid verification code");
    }

    @Override
    public boolean requiresSecondaryAuth(String operation, String scenario) {
        // 高危失能场景豁免二次验证
        if (EMERGENCY_ACTIVATED.equals(scenario)) {
            log.info("Secondary auth exempted for emergency scenario: operation={}", operation);
            return false;
        }

        log.info("Secondary auth required: operation={}, scenario={}", operation, scenario);
        return true;
    }
}
