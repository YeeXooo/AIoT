package com.aiot.domain.model;

import com.aiot.domain.model.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OverrideType 驾驶员覆盖操作类型枚举测试")
class OverrideTypeTest {

    @Nested
    @DisplayName("of() 字符串转覆盖操作类型")
    class OfMethod {

        @Test
        @DisplayName("正常转换 - TURNING")
        void shouldReturnTurning() {
            assertEquals(OverrideType.TURNING, OverrideType.of("TURNING"));
        }

        @Test
        @DisplayName("正常转换 - BRAKING")
        void shouldReturnBraking() {
            assertEquals(OverrideType.BRAKING, OverrideType.of("BRAKING"));
        }

        @Test
        @DisplayName("正常转换 - ACCELERATING")
        void shouldReturnAccelerating() {
            assertEquals(OverrideType.ACCELERATING, OverrideType.of("ACCELERATING"));
        }

        @Test
        @DisplayName("忽略大小写转换")
        void shouldConvertCaseInsensitive() {
            assertEquals(OverrideType.TURNING, OverrideType.of("turning"));
            assertEquals(OverrideType.BRAKING, OverrideType.of("Braking"));
        }

        @Test
        @DisplayName("自动去除前后空格")
        void shouldTrimWhitespace() {
            assertEquals(OverrideType.TURNING, OverrideType.of("  TURNING  "));
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("空值或空白字符串抛出 BusinessException (MODEL_015)")
        void shouldThrowWhenNullOrEmpty(String input) {
            BusinessException ex = assertThrows(BusinessException.class, () -> OverrideType.of(input));
            assertEquals("MODEL_015", ex.getErrorCode());
            assertEquals("OVERRIDE_TYPE_VALIDATE", ex.getErrorScope());
        }

        @Test
        @DisplayName("非法值抛出 BusinessException (MODEL_016)")
        void shouldThrowForInvalidValue() {
            BusinessException ex = assertThrows(BusinessException.class, () -> OverrideType.of("JUMP"));
            assertEquals("MODEL_016", ex.getErrorCode());
            assertEquals("OVERRIDE_TYPE_VALIDATE", ex.getErrorScope());
            assertTrue(ex.getMessage().contains("JUMP"));
        }
    }

    @Test
    @DisplayName("枚举值数量验证")
    void shouldHaveThreeValues() {
        assertEquals(3, OverrideType.values().length);
    }
}
