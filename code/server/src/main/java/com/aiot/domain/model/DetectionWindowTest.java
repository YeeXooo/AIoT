package com.aiot.domain.model;

import com.aiot.domain.model.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DetectionWindow 活体检测判定窗口测试")
class DetectionWindowTest {

    @Nested
    @DisplayName("create() 工厂方法")
    class CreateMethod {

        @Test
        @DisplayName("正常创建")
        void shouldCreateSuccessfully() {
            Instant start = Instant.now();
            DetectionWindow window = DetectionWindow.create(Duration.ofSeconds(30), start, Duration.ofSeconds(5));
            assertEquals(Duration.ofSeconds(30), window.getRemainingTime());
            assertEquals(start, window.getStartTime());
            assertEquals(0, window.getMicroMovementCount());
            assertEquals(Duration.ofSeconds(5), window.getToleranceThreshold());
        }

        @Test
        @DisplayName("remainingTime 为 null 抛出 BusinessException (MODEL_041)")
        void shouldThrowWhenRemainingTimeIsNull() {
            assertThrows(BusinessException.class, () ->
                    DetectionWindow.create(null, Instant.now(), Duration.ofSeconds(5)));
        }

        @Test
        @DisplayName("remainingTime 为负数抛出 BusinessException (MODEL_041)")
        void shouldThrowWhenRemainingTimeIsNegative() {
            assertThrows(BusinessException.class, () ->
                    DetectionWindow.create(Duration.ofSeconds(-1), Instant.now(), Duration.ofSeconds(5)));
        }

        @Test
        @DisplayName("startTime 为 null 抛出 BusinessException (MODEL_042)")
        void shouldThrowWhenStartTimeIsNull() {
            assertThrows(BusinessException.class, () ->
                    DetectionWindow.create(Duration.ofSeconds(30), null, Duration.ofSeconds(5)));
        }

        @Test
        @DisplayName("tolerance 为 null 抛出 BusinessException (MODEL_043)")
        void shouldThrowWhenToleranceIsNull() {
            assertThrows(BusinessException.class, () ->
                    DetectionWindow.create(Duration.ofSeconds(30), Instant.now(), null));
        }

        @Test
        @DisplayName("tolerance 为负数抛出 BusinessException (MODEL_043)")
        void shouldThrowWhenToleranceIsNegative() {
            assertThrows(BusinessException.class, () ->
                    DetectionWindow.create(Duration.ofSeconds(30), Instant.now(), Duration.ofSeconds(-1)));
        }
    }

    @Nested
    @DisplayName("tick() 时间推进")
    class TickMethod {

        @Test
        @DisplayName("正常推进时间")
        void shouldReduceRemainingTime() {
            DetectionWindow window = DetectionWindow.create(Duration.ofSeconds(30), Instant.now(), Duration.ofSeconds(5));
            DetectionWindow ticked = window.tick(Duration.ofSeconds(10));
            assertEquals(Duration.ofSeconds(20), ticked.getRemainingTime());
            assertFalse(ticked.isExpired());
        }

        @Test
        @DisplayName("推进时间不超过剩余时间则归零")
        void shouldNotGoBelowZero() {
            DetectionWindow window = DetectionWindow.create(Duration.ofSeconds(5), Instant.now(), Duration.ofSeconds(1));
            DetectionWindow ticked = window.tick(Duration.ofSeconds(10));
            assertEquals(Duration.ZERO, ticked.getRemainingTime());
            assertTrue(ticked.isExpired());
        }

        @Test
        @DisplayName("tick 不改变原始对象（不可变）")
        void shouldNotMutateOriginal() {
            DetectionWindow window = DetectionWindow.create(Duration.ofSeconds(30), Instant.now(), Duration.ofSeconds(5));
            window.tick(Duration.ofSeconds(10));
            assertEquals(Duration.ofSeconds(30), window.getRemainingTime());
        }
    }

    @Nested
    @DisplayName("incrementCount() 微动计数")
    class IncrementCountMethod {

        @Test
        @DisplayName("增加微动计数")
        void shouldIncrementCount() {
            DetectionWindow window = DetectionWindow.create(Duration.ofSeconds(30), Instant.now(), Duration.ofSeconds(5));
            DetectionWindow incremented = window.incrementCount();
            assertEquals(1, incremented.getMicroMovementCount());
        }

        @Test
        @DisplayName("多次递增")
        void shouldIncrementMultipleTimes() {
            DetectionWindow window = DetectionWindow.create(Duration.ofSeconds(30), Instant.now(), Duration.ofSeconds(5));
            DetectionWindow result = window.incrementCount().incrementCount().incrementCount();
            assertEquals(3, result.getMicroMovementCount());
        }
    }

    @Nested
    @DisplayName("isExpired() 过期判定")
    class IsExpiredMethod {

        @Test
        @DisplayName("未过期")
        void shouldNotBeExpired() {
            DetectionWindow window = DetectionWindow.create(Duration.ofSeconds(30), Instant.now(), Duration.ofSeconds(5));
            assertFalse(window.isExpired());
        }

        @Test
        @DisplayName("已过期 - 剩余时间为零")
        void shouldBeExpiredWhenZero() {
            DetectionWindow window = DetectionWindow.create(Duration.ZERO, Instant.now(), Duration.ofSeconds(5));
            assertTrue(window.isExpired());
        }
    }

    @Nested
    @DisplayName("equals/hashCode")
    class EqualsHashCode {

        @Test
        @DisplayName("相同属性相等")
        void shouldBeEqual() {
            Instant start = Instant.parse("2024-01-01T00:00:00Z");
            DetectionWindow a = DetectionWindow.create(Duration.ofSeconds(30), start, Duration.ofSeconds(5));
            DetectionWindow b = DetectionWindow.create(Duration.ofSeconds(30), start, Duration.ofSeconds(5));
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("不同属性不等")
        void shouldNotBeEqual() {
            Instant start = Instant.parse("2024-01-01T00:00:00Z");
            DetectionWindow a = DetectionWindow.create(Duration.ofSeconds(30), start, Duration.ofSeconds(5));
            DetectionWindow b = DetectionWindow.create(Duration.ofSeconds(20), start, Duration.ofSeconds(5));
            assertNotEquals(a, b);
        }
    }
}
