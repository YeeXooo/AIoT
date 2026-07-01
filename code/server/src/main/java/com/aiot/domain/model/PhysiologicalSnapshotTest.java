package com.aiot.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PhysiologicalSnapshot 生理体征快照 Record 测试")
class PhysiologicalSnapshotTest {

    @Nested
    @DisplayName("正常创建")
    class ValidCreation {

        @Test
        @DisplayName("创建完整快照")
        void shouldCreateFullSnapshot() {
            Instant now = Instant.now();
            PhysiologicalSnapshot snapshot = new PhysiologicalSnapshot(
                    now, 72, 98.5, 0.8, 16, 120, 80, 0.3, 36.5
            );
            assertEquals(now, snapshot.timestamp());
            assertEquals(72, snapshot.heartRate());
            assertEquals(98.5, snapshot.bloodOxygen());
            assertEquals(0.8, snapshot.emotionIndex());
            assertEquals(16, snapshot.respiratoryRate());
            assertEquals(120, snapshot.systolicBp());
            assertEquals(80, snapshot.diastolicBp());
            assertEquals(0.3, snapshot.fatigueIndex());
            assertEquals(36.5, snapshot.bodyTemperature());
        }

        @Test
        @DisplayName("部分字段可为 null")
        void shouldAllowNullableFields() {
            Instant now = Instant.now();
            PhysiologicalSnapshot snapshot = new PhysiologicalSnapshot(
                    now, null, null, null, null, null, null, null, null
            );
            assertEquals(now, snapshot.timestamp());
            assertNull(snapshot.heartRate());
            assertNull(snapshot.bloodOxygen());
        }
    }

    @Nested
    @DisplayName("参数校验")
    class Validation {

        @Test
        @DisplayName("timestamp 为 null 抛出 NullPointerException")
        void shouldThrowWhenTimestampIsNull() {
            assertThrows(NullPointerException.class, () ->
                    new PhysiologicalSnapshot(null, 72, 98.5, 0.8, 16, 120, 80, 0.3, 36.5)
            );
        }
    }

    @Nested
    @DisplayName("Record 特性")
    class RecordFeatures {

        @Test
        @DisplayName("equals 相等")
        void shouldBeEqual() {
            Instant ts = Instant.parse("2024-01-01T00:00:00Z");
            PhysiologicalSnapshot a = new PhysiologicalSnapshot(ts, 72, 98.5, 0.8, 16, 120, 80, 0.3, 36.5);
            PhysiologicalSnapshot b = new PhysiologicalSnapshot(ts, 72, 98.5, 0.8, 16, 120, 80, 0.3, 36.5);
            assertEquals(a, b);
        }

        @Test
        @DisplayName("equals 不等 - 不同心率")
        void shouldNotBeEqualDifferentHeartRate() {
            Instant ts = Instant.parse("2024-01-01T00:00:00Z");
            PhysiologicalSnapshot a = new PhysiologicalSnapshot(ts, 72, 98.5, 0.8, 16, 120, 80, 0.3, 36.5);
            PhysiologicalSnapshot b = new PhysiologicalSnapshot(ts, 80, 98.5, 0.8, 16, 120, 80, 0.3, 36.5);
            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("hashCode 一致")
        void shouldHaveSameHashCode() {
            Instant ts = Instant.parse("2024-01-01T00:00:00Z");
            PhysiologicalSnapshot a = new PhysiologicalSnapshot(ts, 72, 98.5, 0.8, 16, 120, 80, 0.3, 36.5);
            PhysiologicalSnapshot b = new PhysiologicalSnapshot(ts, 72, 98.5, 0.8, 16, 120, 80, 0.3, 36.5);
            assertEquals(a.hashCode(), b.hashCode());
        }
    }
}
