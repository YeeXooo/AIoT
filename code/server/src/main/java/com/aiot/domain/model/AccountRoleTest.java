package com.aiot.domain.model;

import com.aiot.domain.model.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AccountRole 账户角色枚举测试")
class AccountRoleTest {

    @Nested
    @DisplayName("of() 字符串转账户角色")
    class OfMethod {

        @Test
        @DisplayName("正常转换 - FAMILY")
        void shouldReturnFamilyForValidCode() {
            assertEquals(AccountRole.FAMILY, AccountRole.of("FAMILY"));
        }

        @Test
        @DisplayName("正常转换 - MANAGER")
        void shouldReturnManagerForValidCode() {
            assertEquals(AccountRole.MANAGER, AccountRole.of("MANAGER"));
        }

        @Test
        @DisplayName("正常转换 - RESCUE")
        void shouldReturnRescueForValidCode() {
            assertEquals(AccountRole.RESCUE, AccountRole.of("RESCUE"));
        }

        @Test
        @DisplayName("忽略大小写转换")
        void shouldConvertCaseInsensitive() {
            assertEquals(AccountRole.FAMILY, AccountRole.of("family"));
            assertEquals(AccountRole.MANAGER, AccountRole.of("Manager"));
            assertEquals(AccountRole.RESCUE, AccountRole.of("rescue"));
        }

        @Test
        @DisplayName("自动去除前后空格")
        void shouldTrimWhitespace() {
            assertEquals(AccountRole.FAMILY, AccountRole.of("  FAMILY  "));
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("空值或空白字符串抛出 BusinessException (MODEL_007)")
        void shouldThrowWhenNullOrEmpty(String input) {
            BusinessException ex = assertThrows(BusinessException.class, () -> AccountRole.of(input));
            assertEquals("MODEL_007", ex.getErrorCode());
            assertEquals("ACCOUNT_ROLE_VALIDATE", ex.getErrorScope());
        }

        @Test
        @DisplayName("非法值抛出 BusinessException (MODEL_008)")
        void shouldThrowForInvalidValue() {
            BusinessException ex = assertThrows(BusinessException.class, () -> AccountRole.of("ADMIN"));
            assertEquals("MODEL_008", ex.getErrorCode());
            assertEquals("ACCOUNT_ROLE_VALIDATE", ex.getErrorScope());
            assertTrue(ex.getMessage().contains("ADMIN"));
        }
    }

    @Nested
    @DisplayName("角色判断方法")
    class RoleCheckMethods {

        @Test
        @DisplayName("isFamily() 仅 FAMILY 返回 true")
        void isFamilyShouldReturnTrueOnlyForFamily() {
            assertTrue(AccountRole.FAMILY.isFamily());
            assertFalse(AccountRole.MANAGER.isFamily());
            assertFalse(AccountRole.RESCUE.isFamily());
        }

        @Test
        @DisplayName("isManager() 仅 MANAGER 返回 true")
        void isManagerShouldReturnTrueOnlyForManager() {
            assertFalse(AccountRole.FAMILY.isManager());
            assertTrue(AccountRole.MANAGER.isManager());
            assertFalse(AccountRole.RESCUE.isManager());
        }

        @Test
        @DisplayName("isRescue() 仅 RESCUE 返回 true")
        void isRescueShouldReturnTrueOnlyForRescue() {
            assertFalse(AccountRole.FAMILY.isRescue());
            assertFalse(AccountRole.MANAGER.isRescue());
            assertTrue(AccountRole.RESCUE.isRescue());
        }
    }

    @Test
    @DisplayName("枚举值数量验证")
    void shouldHaveThreeValues() {
        assertEquals(3, AccountRole.values().length);
    }
}
