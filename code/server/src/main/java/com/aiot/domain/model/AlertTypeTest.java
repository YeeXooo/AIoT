package com.aiot.domain.model;

import com.aiot.domain.model.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AlertType 告警类型枚举测试")
class AlertTypeTest {

    @Nested
    @DisplayName("of() 字符串转告警类型")
    class OfMethod {

        @Test
        @DisplayName("正常转换 - FATIGUE")
        void shouldReturnFatigue() {
            assertEquals(AlertType.FATIGUE, AlertType.of("FATIGUE"));
        }

        @Test
        @DisplayName("正常转换 - DISTRACTION")
        void shouldReturnDistraction() {
            assertEquals(AlertType.DISTRACTION, AlertType.of("DISTRACTION"));
        }

        @Test
        @DisplayName("正常转换 - ROAD_RAGE")
        void shouldReturnRoadRage() {
            assertEquals(AlertType.ROAD_RAGE, AlertType.of("ROAD_RAGE"));
        }

        @Test
        @DisplayName("正常转换 - LIFE_DETECTION")
        void shouldReturnLifeDetection() {
            assertEquals(AlertType.LIFE_DETECTION, AlertType.of("LIFE_DETECTION"));
        }

        @Test
        @DisplayName("正常转换 - COLLISION_DISABILITY")
        void shouldReturnCollisionDisability() {
            assertEquals(AlertType.COLLISION_DISABILITY, AlertType.of("COLLISION_DISABILITY"));
        }

        @Test
        @DisplayName("正常转换 - PERFORMANCE_WARNING")
        void shouldReturnPerformanceWarning() {
            assertEquals(AlertType.PERFORMANCE_WARNING, AlertType.of("PERFORMANCE_WARNING"));
        }

        @Test
        @DisplayName("忽略大小写转换")
        void shouldConvertCaseInsensitive() {
            assertEquals(AlertType.FATIGUE, AlertType.of("fatigue"));
            assertEquals(AlertType.ROAD_RAGE, AlertType.of("Road_Rage"));
        }

        @Test
        @DisplayName("自动去除前后空格")
        void shouldTrimWhitespace() {
            assertEquals(AlertType.FATIGUE, AlertType.of("  FATIGUE  "));
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("空值或空白字符串抛出 BusinessException (MODEL_003)")
        void shouldThrowWhenNullOrEmpty(String input) {
            BusinessException ex = assertThrows(BusinessException.class, () -> AlertType.of(input));
            assertEquals("MODEL_003", ex.getErrorCode());
            assertEquals("ALERT_TYPE_VALIDATE", ex.getErrorScope());
        }

        @Test
        @DisplayName("非法值抛出 BusinessException (MODEL_004)")
        void shouldThrowForInvalidValue() {
            BusinessException ex = assertThrows(BusinessException.class, () -> AlertType.of("UNKNOWN"));
            assertEquals("MODEL_004", ex.getErrorCode());
            assertEquals("ALERT_TYPE_VALIDATE", ex.getErrorScope());
            assertTrue(ex.getMessage().contains("UNKNOWN"));
        }
    }

    @Test
    @DisplayName("枚举值数量验证")
    void shouldHaveSixValues() {
        assertEquals(6, AlertType.values().length);
    }
}
