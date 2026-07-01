package com.aiot.domain.model;

import com.aiot.domain.model.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DriverComprehensiveScore 驾驶员综合风险评分测试")
class DriverComprehensiveScoreTest {

    @Nested
    @DisplayName("of() 工厂方法")
    class OfMethod {

        @Test
        @DisplayName("正常创建 - 中间值")
        void shouldCreateWithValidValue() {
            DriverComprehensiveScore score = DriverComprehensiveScore.of(75);
            assertEquals(75, score.getValue());
        }

        @Test
        @DisplayName("边界值 - 0")
        void shouldCreateWithZero() {
            DriverComprehensiveScore score = DriverComprehensiveScore.of(0);
            assertEquals(0, score.getValue());
        }

        @Test
        @DisplayName("边界值 - 100")
        void shouldCreateWithHundred() {
            DriverComprehensiveScore score = DriverComprehensiveScore.of(100);
            assertEquals(100, score.getValue());
        }

        @ParameterizedTest
        @ValueSource(ints = {-1, -10, -100})
        @DisplayName("负数抛出 BusinessException (MODEL_046)")
        void shouldThrowForNegativeValue(int value) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> DriverComprehensiveScore.of(value));
            assertEquals("MODEL_046", ex.getErrorCode());
            assertEquals("DRIVER_COMPREHENSIVE_SCORE_VALIDATE", ex.getErrorScope());
        }

        @ParameterizedTest
        @ValueSource(ints = {101, 150, 200, 1000})
        @DisplayName("超过 100 抛出 BusinessException (MODEL_046)")
        void shouldThrowForValueOver100(int value) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> DriverComprehensiveScore.of(value));
            assertEquals("MODEL_046", ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("equals/hashCode")
    class EqualsHashCode {

        @Test
        @DisplayName("相同分值相等")
        void shouldBeEqual() {
            DriverComprehensiveScore a = DriverComprehensiveScore.of(80);
            DriverComprehensiveScore b = DriverComprehensiveScore.of(80);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("不同分值不等")
        void shouldNotBeEqual() {
            DriverComprehensiveScore a = DriverComprehensiveScore.of(80);
            DriverComprehensiveScore b = DriverComprehensiveScore.of(60);
            assertNotEquals(a, b);
        }
    }
}
