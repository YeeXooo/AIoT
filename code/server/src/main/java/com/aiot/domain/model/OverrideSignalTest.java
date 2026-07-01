package com.aiot.domain.model;

import com.aiot.domain.model.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OverrideSignal 驾驶员覆盖信号测试")
class OverrideSignalTest {

    @Nested
    @DisplayName("of() 工厂方法")
    class OfMethod {

        @Test
        @DisplayName("正常创建 - TURNING")
        void shouldCreateTurningSignal() {
            Instant now = Instant.now();
            OverrideSignal signal = OverrideSignal.of(OverrideType.TURNING, now);
            assertEquals(OverrideType.TURNING, signal.getType());
            assertEquals(now, signal.getTimestamp());
        }

        @Test
        @DisplayName("正常创建 - BRAKING")
        void shouldCreateBrakingSignal() {
            OverrideSignal signal = OverrideSignal.of(OverrideType.BRAKING, Instant.now());
            assertEquals(OverrideType.BRAKING, signal.getType());
        }

        @Test
        @DisplayName("正常创建 - ACCELERATING")
        void shouldCreateAcceleratingSignal() {
            OverrideSignal signal = OverrideSignal.of(OverrideType.ACCELERATING, Instant.now());
            assertEquals(OverrideType.ACCELERATING, signal.getType());
        }

        @Test
        @DisplayName("type 为 null 抛出 BusinessException (MODEL_044)")
        void shouldThrowWhenTypeIsNull() {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    OverrideSignal.of(null, Instant.now()));
            assertEquals("MODEL_044", ex.getErrorCode());
            assertEquals("OVERRIDE_SIGNAL_VALIDATE", ex.getErrorScope());
        }

        @Test
        @DisplayName("timestamp 为 null 抛出 BusinessException (MODEL_045)")
        void shouldThrowWhenTimestampIsNull() {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    OverrideSignal.of(OverrideType.TURNING, null));
            assertEquals("MODEL_045", ex.getErrorCode());
            assertEquals("OVERRIDE_SIGNAL_VALIDATE", ex.getErrorScope());
        }
    }

    @Nested
    @DisplayName("equals/hashCode")
    class EqualsHashCode {

        @Test
        @DisplayName("相同属性相等")
        void shouldBeEqual() {
            Instant ts = Instant.parse("2024-01-01T00:00:00Z");
            OverrideSignal a = OverrideSignal.of(OverrideType.TURNING, ts);
            OverrideSignal b = OverrideSignal.of(OverrideType.TURNING, ts);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("不同类型不等")
        void shouldNotBeEqualDifferentType() {
            Instant ts = Instant.parse("2024-01-01T00:00:00Z");
            OverrideSignal a = OverrideSignal.of(OverrideType.TURNING, ts);
            OverrideSignal b = OverrideSignal.of(OverrideType.BRAKING, ts);
            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("不同时间戳不等")
        void shouldNotBeEqualDifferentTimestamp() {
            OverrideSignal a = OverrideSignal.of(OverrideType.TURNING, Instant.parse("2024-01-01T00:00:00Z"));
            OverrideSignal b = OverrideSignal.of(OverrideType.TURNING, Instant.parse("2024-01-01T00:00:01Z"));
            assertNotEquals(a, b);
        }
    }
}
