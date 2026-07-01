package com.aiot.domain.model;

import com.aiot.domain.model.exception.BusinessException;
import com.aiot.domain.shared.DriverId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DriverStatusSnapshot 驾驶员状态快照测试")
class DriverStatusSnapshotTest {

    @Nested
    @DisplayName("of() 工厂方法")
    class OfMethod {

        @Test
        @DisplayName("正常创建 - 绿色状态")
        void shouldCreateGreenSnapshot() {
            DriverId driverId = DriverId.of("DRV-001");
            Instant now = Instant.now();
            DriverStatusSnapshot snapshot = DriverStatusSnapshot.of(driverId, StatusColor.GREEN, now);
            assertEquals(driverId, snapshot.getDriverId());
            assertEquals(StatusColor.GREEN, snapshot.getStatusColor());
            assertEquals(now, snapshot.getTimestamp());
        }

        @Test
        @DisplayName("正常创建 - 黄色状态")
        void shouldCreateYellowSnapshot() {
            DriverStatusSnapshot snapshot = DriverStatusSnapshot.of(
                    DriverId.of("DRV-001"), StatusColor.YELLOW, Instant.now());
            assertEquals(StatusColor.YELLOW, snapshot.getStatusColor());
        }

        @Test
        @DisplayName("正常创建 - 红色状态")
        void shouldCreateRedSnapshot() {
            DriverStatusSnapshot snapshot = DriverStatusSnapshot.of(
                    DriverId.of("DRV-001"), StatusColor.RED, Instant.now());
            assertEquals(StatusColor.RED, snapshot.getStatusColor());
        }

        @Test
        @DisplayName("driverId 为 null 抛出 BusinessException (MODEL_032)")
        void shouldThrowWhenDriverIdIsNull() {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    DriverStatusSnapshot.of(null, StatusColor.GREEN, Instant.now()));
            assertEquals("MODEL_032", ex.getErrorCode());
            assertEquals("DRIVER_STATUS_SNAPSHOT_VALIDATE", ex.getErrorScope());
        }

        @Test
        @DisplayName("statusColor 为 null 抛出 BusinessException (MODEL_033)")
        void shouldThrowWhenStatusColorIsNull() {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    DriverStatusSnapshot.of(DriverId.of("DRV-001"), null, Instant.now()));
            assertEquals("MODEL_033", ex.getErrorCode());
        }

        @Test
        @DisplayName("timestamp 为 null 抛出 BusinessException (MODEL_034)")
        void shouldThrowWhenTimestampIsNull() {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    DriverStatusSnapshot.of(DriverId.of("DRV-001"), StatusColor.GREEN, null));
            assertEquals("MODEL_034", ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("equals/hashCode")
    class EqualsHashCode {

        @Test
        @DisplayName("相同属性相等")
        void shouldBeEqual() {
            DriverId id = DriverId.of("DRV-001");
            Instant ts = Instant.parse("2024-01-01T00:00:00Z");
            DriverStatusSnapshot a = DriverStatusSnapshot.of(id, StatusColor.GREEN, ts);
            DriverStatusSnapshot b = DriverStatusSnapshot.of(id, StatusColor.GREEN, ts);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("不同状态色不等")
        void shouldNotBeEqualDifferentColor() {
            DriverId id = DriverId.of("DRV-001");
            Instant ts = Instant.parse("2024-01-01T00:00:00Z");
            DriverStatusSnapshot a = DriverStatusSnapshot.of(id, StatusColor.GREEN, ts);
            DriverStatusSnapshot b = DriverStatusSnapshot.of(id, StatusColor.RED, ts);
            assertNotEquals(a, b);
        }
    }
}
