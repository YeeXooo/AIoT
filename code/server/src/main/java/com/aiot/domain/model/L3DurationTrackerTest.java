package com.aiot.domain.model;

import com.aiot.domain.model.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("L3DurationTracker L3持续时长追踪器测试")
class L3DurationTrackerTest {

    @Nested
    @DisplayName("start() 启动追踪")
    class StartMethod {

        @Test
        @DisplayName("正常启动")
        void shouldStartSuccessfully() {
            Instant now = Instant.now();
            L3DurationTracker tracker = L3DurationTracker.start(now);
            assertEquals(now, tracker.getStartTime());
            assertEquals(Duration.ZERO, tracker.getAccumulatedDuration());
            assertTrue(tracker.isActive());
        }

        @Test
        @DisplayName("startTime 为 null 抛出 BusinessException (MODEL_036)")
        void shouldThrowWhenStartTimeIsNull() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> L3DurationTracker.start(null));
            assertEquals("MODEL_036", ex.getErrorCode());
            assertEquals("L3_DURATION_TRACKER_VALIDATE", ex.getErrorScope());
        }
    }

    @Nested
    @DisplayName("advance() 推进时间")
    class AdvanceMethod {

        @Test
        @DisplayName("活跃状态下推进时间")
        void shouldAdvanceWhenActive() {
            Instant start = Instant.parse("2024-01-01T00:00:00Z");
            L3DurationTracker tracker = L3DurationTracker.start(start);
            Instant later = start.plusSeconds(30);
            L3DurationTracker advanced = tracker.advance(later);
            assertEquals(Duration.ofSeconds(30), advanced.getAccumulatedDuration());
            assertTrue(advanced.isActive());
        }

        @Test
        @DisplayName("停止状态下推进不改变累计时长")
        void shouldNotAdvanceWhenStopped() {
            Instant start = Instant.parse("2024-01-01T00:00:00Z");
            L3DurationTracker tracker = L3DurationTracker.start(start).stop();
            Instant later = start.plusSeconds(30);
            L3DurationTracker advanced = tracker.advance(later);
            assertEquals(Duration.ZERO, advanced.getAccumulatedDuration());
            assertFalse(advanced.isActive());
        }

        @Test
        @DisplayName("不改变原始对象（不可变）")
        void shouldNotMutateOriginal() {
            Instant start = Instant.parse("2024-01-01T00:00:00Z");
            L3DurationTracker tracker = L3DurationTracker.start(start);
            tracker.advance(start.plusSeconds(30));
            assertEquals(Duration.ZERO, tracker.getAccumulatedDuration());
        }
    }

    @Nested
    @DisplayName("stop() 停止追踪")
    class StopMethod {

        @Test
        @DisplayName("停止追踪")
        void shouldStopSuccessfully() {
            Instant start = Instant.parse("2024-01-01T00:00:00Z");
            L3DurationTracker tracker = L3DurationTracker.start(start).stop();
            assertFalse(tracker.isActive());
            assertEquals(Duration.ZERO, tracker.getAccumulatedDuration());
        }

        @Test
        @DisplayName("停止后保留已累计时长")
        void shouldRetainAccumulatedDuration() {
            Instant start = Instant.parse("2024-01-01T00:00:00Z");
            L3DurationTracker tracker = L3DurationTracker.start(start)
                    .advance(start.plusSeconds(30))
                    .stop();
            assertFalse(tracker.isActive());
            assertEquals(Duration.ofSeconds(30), tracker.getAccumulatedDuration());
        }
    }

    @Nested
    @DisplayName("equals/hashCode")
    class EqualsHashCode {

        @Test
        @DisplayName("相同属性相等")
        void shouldBeEqual() {
            Instant start = Instant.parse("2024-01-01T00:00:00Z");
            L3DurationTracker a = L3DurationTracker.start(start);
            L3DurationTracker b = L3DurationTracker.start(start);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("不同状态不等")
        void shouldNotBeEqual() {
            Instant start = Instant.parse("2024-01-01T00:00:00Z");
            L3DurationTracker a = L3DurationTracker.start(start);
            L3DurationTracker b = L3DurationTracker.start(start).stop();
            assertNotEquals(a, b);
        }
    }
}
