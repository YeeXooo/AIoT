package com.aiot.domain.model;

import com.aiot.domain.model.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OTAUpgradeStatus OTA升级状态测试")
class OTAUpgradeStatusTest {

    private final OTAVersion version = OTAVersion.of("2.0.0", "ModelX", "abc123");

    @Nested
    @DisplayName("init() 初始化")
    class InitMethod {

        @Test
        @DisplayName("正常初始化")
        void shouldInitSuccessfully() {
            Instant now = Instant.now();
            OTAUpgradeStatus status = OTAUpgradeStatus.init(version, now);
            assertEquals(UpgradeStage.PENDING, status.getStage());
            assertEquals(version, status.getTargetVersion());
            assertEquals(0, status.getOffset());
            assertEquals(now, status.getStageTimestamp());
        }
    }

    @Nested
    @DisplayName("transition() 状态转换")
    class TransitionMethod {

        @Test
        @DisplayName("从 PENDING 转换到 DOWNLOADING")
        void shouldTransitionToDownloading() {
            Instant start = Instant.parse("2024-01-01T00:00:00Z");
            OTAUpgradeStatus status = OTAUpgradeStatus.init(version, start);
            Instant now = start.plusSeconds(10);
            OTAUpgradeStatus downloading = status.transition(UpgradeStage.DOWNLOADING, 1024, now);
            assertEquals(UpgradeStage.DOWNLOADING, downloading.getStage());
            assertEquals(1024, downloading.getOffset());
            assertEquals(now, downloading.getStageTimestamp());
            assertEquals(version, downloading.getTargetVersion());
        }

        @Test
        @DisplayName("不改变原始对象（不可变）")
        void shouldNotMutateOriginal() {
            Instant start = Instant.parse("2024-01-01T00:00:00Z");
            OTAUpgradeStatus status = OTAUpgradeStatus.init(version, start);
            status.transition(UpgradeStage.DOWNLOADING, 1024, start.plusSeconds(10));
            assertEquals(UpgradeStage.PENDING, status.getStage());
            assertEquals(0, status.getOffset());
        }
    }

    @Nested
    @DisplayName("参数校验")
    class Validation {

        @Test
        @DisplayName("transition 中 stage 为 null 抛出 BusinessException (MODEL_039)")
        void shouldThrowWhenStageIsNull() {
            Instant start = Instant.parse("2024-01-01T00:00:00Z");
            OTAUpgradeStatus status = OTAUpgradeStatus.init(version, start);
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    status.transition(null, 0, start.plusSeconds(10)));
            assertEquals("MODEL_039", ex.getErrorCode());
            assertEquals("OTA_UPGRADE_STATUS_VALIDATE", ex.getErrorScope());
        }

        @Test
        @DisplayName("offset 为负数抛出 BusinessException (MODEL_040)")
        void shouldThrowWhenOffsetIsNegative() {
            Instant start = Instant.parse("2024-01-01T00:00:00Z");
            OTAUpgradeStatus status = OTAUpgradeStatus.init(version, start);
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    status.transition(UpgradeStage.DOWNLOADING, -1, start.plusSeconds(10)));
            assertEquals("MODEL_040", ex.getErrorCode());
            assertEquals("OTA_UPGRADE_STATUS_VALIDATE", ex.getErrorScope());
        }
    }

    @Nested
    @DisplayName("equals/hashCode")
    class EqualsHashCode {

        @Test
        @DisplayName("相同属性相等")
        void shouldBeEqual() {
            Instant start = Instant.parse("2024-01-01T00:00:00Z");
            OTAUpgradeStatus a = OTAUpgradeStatus.init(version, start);
            OTAUpgradeStatus b = OTAUpgradeStatus.init(version, start);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("不同阶段不等")
        void shouldNotBeEqualDifferentStage() {
            Instant start = Instant.parse("2024-01-01T00:00:00Z");
            OTAUpgradeStatus a = OTAUpgradeStatus.init(version, start);
            OTAUpgradeStatus b = a.transition(UpgradeStage.DOWNLOADING, 0, start);
            assertNotEquals(a, b);
        }
    }
}
