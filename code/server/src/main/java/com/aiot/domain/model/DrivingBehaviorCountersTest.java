package com.aiot.domain.model;

import com.aiot.domain.model.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DrivingBehaviorCounters 驾驶行为计数器测试")
class DrivingBehaviorCountersTest {

    @Nested
    @DisplayName("of() 工厂方法")
    class OfMethod {

        @Test
        @DisplayName("正常创建")
        void shouldCreateSuccessfully() {
            DrivingBehaviorCounters counters = DrivingBehaviorCounters.of(5, 3);
            assertEquals(5, counters.getSuddenBrakingCount());
            assertEquals(3, counters.getSuddenAccelerationCount());
        }

        @Test
        @DisplayName("零值创建")
        void shouldCreateWithZeros() {
            DrivingBehaviorCounters counters = DrivingBehaviorCounters.of(0, 0);
            assertEquals(0, counters.getSuddenBrakingCount());
            assertEquals(0, counters.getSuddenAccelerationCount());
        }

        @Test
        @DisplayName("急刹次数为负数抛出 BusinessException (MODEL_035)")
        void shouldThrowForNegativeBraking() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> DrivingBehaviorCounters.of(-1, 0));
            assertEquals("MODEL_035", ex.getErrorCode());
            assertEquals("DRIVING_BEHAVIOR_COUNTERS_VALIDATE", ex.getErrorScope());
        }

        @Test
        @DisplayName("急加速次数为负数抛出 BusinessException (MODEL_035)")
        void shouldThrowForNegativeAcceleration() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> DrivingBehaviorCounters.of(0, -1));
            assertEquals("MODEL_035", ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("init() 初始化")
    class InitMethod {

        @Test
        @DisplayName("初始化为零")
        void shouldInitWithZeros() {
            DrivingBehaviorCounters counters = DrivingBehaviorCounters.init();
            assertEquals(0, counters.getSuddenBrakingCount());
            assertEquals(0, counters.getSuddenAccelerationCount());
        }
    }

    @Nested
    @DisplayName("incrementBraking() 急刹递增")
    class IncrementBrakingMethod {

        @Test
        @DisplayName("递增急刹计数")
        void shouldIncrementBraking() {
            DrivingBehaviorCounters counters = DrivingBehaviorCounters.init();
            DrivingBehaviorCounters result = counters.incrementBraking();
            assertEquals(1, result.getSuddenBrakingCount());
            assertEquals(0, result.getSuddenAccelerationCount());
        }

        @Test
        @DisplayName("不改变原始对象（不可变）")
        void shouldNotMutateOriginal() {
            DrivingBehaviorCounters counters = DrivingBehaviorCounters.init();
            counters.incrementBraking();
            assertEquals(0, counters.getSuddenBrakingCount());
        }
    }

    @Nested
    @DisplayName("incrementAcceleration() 急加速递增")
    class IncrementAccelerationMethod {

        @Test
        @DisplayName("递增急加速计数")
        void shouldIncrementAcceleration() {
            DrivingBehaviorCounters counters = DrivingBehaviorCounters.init();
            DrivingBehaviorCounters result = counters.incrementAcceleration();
            assertEquals(0, result.getSuddenBrakingCount());
            assertEquals(1, result.getSuddenAccelerationCount());
        }
    }

    @Nested
    @DisplayName("链式操作")
    class ChainedOperations {

        @Test
        @DisplayName("连续递增")
        void shouldChainIncrements() {
            DrivingBehaviorCounters result = DrivingBehaviorCounters.init()
                    .incrementBraking()
                    .incrementBraking()
                    .incrementAcceleration();
            assertEquals(2, result.getSuddenBrakingCount());
            assertEquals(1, result.getSuddenAccelerationCount());
        }
    }

    @Nested
    @DisplayName("equals/hashCode")
    class EqualsHashCode {

        @Test
        @DisplayName("相同计数相等")
        void shouldBeEqual() {
            DrivingBehaviorCounters a = DrivingBehaviorCounters.of(3, 5);
            DrivingBehaviorCounters b = DrivingBehaviorCounters.of(3, 5);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("不同计数不等")
        void shouldNotBeEqual() {
            DrivingBehaviorCounters a = DrivingBehaviorCounters.of(3, 5);
            DrivingBehaviorCounters b = DrivingBehaviorCounters.of(3, 6);
            assertNotEquals(a, b);
        }
    }
}
