package com.aiot.domain.model;

import com.aiot.domain.model.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Permission 访问权限测试")
class PermissionTest {

    @Nested
    @DisplayName("of() 工厂方法")
    class OfMethod {

        @Test
        @DisplayName("正常创建 - 带操作集合")
        void shouldCreateWithOperations() {
            Permission permission = Permission.of(Set.of("VIEW_STATUS", "VIEW_ALERTS"));
            assertEquals(2, permission.getOperations().size());
            assertTrue(permission.getOperations().contains("VIEW_STATUS"));
            assertTrue(permission.getOperations().contains("VIEW_ALERTS"));
        }

        @Test
        @DisplayName("null 集合转为空集合")
        void shouldReturnEmptySetForNull() {
            Permission permission = Permission.of(null);
            assertTrue(permission.getOperations().isEmpty());
        }

        @Test
        @DisplayName("集合不可变")
        void shouldReturnUnmodifiableSet() {
            Permission permission = Permission.of(Set.of("VIEW_STATUS"));
            assertThrows(UnsupportedOperationException.class, () ->
                    permission.getOperations().add("NEW_OP"));
        }

        @Test
        @DisplayName("包含空值元素抛出 BusinessException (MODEL_023)")
        void shouldThrowWhenContainsBlankElement() {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    Permission.of(Set.of("VIEW_STATUS", "")));
            assertEquals("MODEL_023", ex.getErrorCode());
            assertEquals("PERMISSION_VALIDATE", ex.getErrorScope());
        }

        @Test
        @DisplayName("包含 null 元素抛出 BusinessException (MODEL_023)")
        void shouldThrowWhenContainsNullElement() {
            // Using a mutable set to add null since Set.of doesn't allow null
            java.util.HashSet<String> ops = new java.util.HashSet<>();
            ops.add("VIEW_STATUS");
            ops.add(null);
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    Permission.of(ops));
            assertEquals("MODEL_023", ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("revoked() 撤销权限")
    class RevokedMethod {

        @Test
        @DisplayName("撤销后为空集合")
        void shouldReturnEmptyPermission() {
            Permission permission = Permission.revoked();
            assertTrue(permission.getOperations().isEmpty());
        }

        @Test
        @DisplayName("撤销后 isRevoked 返回 true")
        void isRevokedShouldReturnTrue() {
            Permission permission = Permission.revoked();
            assertTrue(permission.isRevoked());
        }
    }

    @Nested
    @DisplayName("isRevoked() 权限判定")
    class IsRevokedMethod {

        @Test
        @DisplayName("有权限时返回 false")
        void shouldReturnFalseWhenHasPermissions() {
            Permission permission = Permission.of(Set.of("VIEW_STATUS"));
            assertFalse(permission.isRevoked());
        }

        @Test
        @DisplayName("空集合时返回 true")
        void shouldReturnTrueWhenEmpty() {
            Permission permission = Permission.of(Set.of());
            assertTrue(permission.isRevoked());
        }
    }

    @Nested
    @DisplayName("equals/hashCode")
    class EqualsHashCode {

        @Test
        @DisplayName("相同操作集合相等")
        void shouldBeEqual() {
            Permission a = Permission.of(Set.of("VIEW_STATUS", "VIEW_ALERTS"));
            Permission b = Permission.of(Set.of("VIEW_STATUS", "VIEW_ALERTS"));
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("不同操作集合不等")
        void shouldNotBeEqual() {
            Permission a = Permission.of(Set.of("VIEW_STATUS"));
            Permission b = Permission.of(Set.of("VIEW_ALERTS"));
            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("撤销权限与空集合权限相等")
        void revokedShouldEqualEmptySet() {
            Permission revoked = Permission.revoked();
            Permission empty = Permission.of(Set.of());
            assertEquals(revoked, empty);
        }
    }
}
