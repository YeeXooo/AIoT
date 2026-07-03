package com.aiot.domain.model;

import com.aiot.domain.shared.DriverId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("领域模型 reconstitute 重建")
class DomainModelReconstituteTest {

    @Test
    @DisplayName("Driver.reconstitute 从持久化字段正确重建领域对象")
    void driverReconstituteReconstructsCorrectly() {
        Driver d = Driver.reconstitute(
                new DriverId("d-uuid-001"), "测试司机", "13800138000",
                80, 2, java.time.LocalDateTime.of(2026, 1, 1, 0, 0),
                java.time.LocalDateTime.of(2026, 6, 1, 0, 0));

        assertEquals("d-uuid-001", d.driverId().id());
        assertEquals("测试司机", d.name());
        assertEquals("13800138000", d.phone());
        assertEquals(80, d.comprehensiveScore().get().getValue());
        assertEquals(2, d.version());
        assertTrue(d.isActive());
    }

    @Test
    @DisplayName("Driver.reconstitute 正确处理 comprehensiveScore 为 null")
    void driverReconstituteHandlesNullScore() {
        Driver d = Driver.reconstitute(
                new DriverId("d-uuid-002"), "无评分", "13900139000",
                null, 1, java.time.LocalDateTime.of(2026, 1, 1, 0, 0),
                java.time.LocalDateTime.of(2026, 1, 1, 0, 0));

        assertTrue(d.comprehensiveScore().isEmpty());
    }

    @Test
    @DisplayName("SystemAccount.reconstitute 正确重建并维护枚举角色")
    void systemAccountReconstitutePreservesRole() {
        SystemAccount a = SystemAccount.reconstitute(
                new com.aiot.domain.shared.AccountId("acc-001"), "13800138000",
                AccountRole.MANAGER, 3,
                java.time.LocalDateTime.of(2026, 1, 1, 0, 0),
                java.time.LocalDateTime.of(2026, 6, 1, 0, 0));

        assertEquals("acc-001", a.accountId().id());
        assertEquals(AccountRole.MANAGER, a.role());
        assertEquals(3, a.version());
        assertTrue(a.isActive());
    }

    @Test
    @DisplayName("Trip.reconstitute 正确重建含驾驶行为计数器和评分")
    void tripReconstituteReconstructsCountersAndScore() {
        com.aiot.domain.shared.TripId tripId = new com.aiot.domain.shared.TripId("trip-001");
        com.aiot.domain.shared.DriverId driverId = new com.aiot.domain.shared.DriverId("d-001");
        com.aiot.domain.shared.VehicleId vehicleId = new com.aiot.domain.shared.VehicleId("v-001");
        java.time.LocalDateTime started = java.time.LocalDateTime.of(2026, 7, 1, 8, 0);
        java.time.LocalDateTime ended = java.time.LocalDateTime.of(2026, 7, 1, 12, 0);

        Trip t = Trip.reconstitute(tripId, driverId, vehicleId, started, ended,
                3, 2, 75, 1,
                java.time.LocalDateTime.of(2026, 7, 1, 8, 0),
                java.time.LocalDateTime.of(2026, 7, 1, 12, 0));

        assertEquals("trip-001", t.tripId().id());
        assertTrue(t.endedAt().isPresent());
        assertEquals(3, t.drivingBehaviorCounters().getSuddenBrakingCount());
        assertEquals(2, t.drivingBehaviorCounters().getSuddenAccelerationCount());
        assertEquals(75, t.tripScore().get().getValue());
    }

    @Test
    @DisplayName("Trip.reconstitute 处理未结束行程（endedAt 为 null）")
    void tripReconstituteHandlesUnendedTrip() {
        Trip t = Trip.reconstitute(
                new com.aiot.domain.shared.TripId("trip-002"),
                new com.aiot.domain.shared.DriverId("d-002"),
                new com.aiot.domain.shared.VehicleId("v-002"),
                java.time.LocalDateTime.of(2026, 7, 1, 8, 0), null,
                0, 0, null, 1,
                java.time.LocalDateTime.of(2026, 7, 1, 8, 0),
                java.time.LocalDateTime.of(2026, 7, 1, 8, 0));

        assertTrue(t.endedAt().isEmpty());
        assertTrue(t.tripScore().isEmpty());
        assertFalse(t.isEnded());
    }

    @Test
    @DisplayName("Vehicle.reconstitute 正确处理可选字段")
    void vehicleReconstituteHandlesOptionalFields() {
        Vehicle v = Vehicle.reconstitute(
                new com.aiot.domain.shared.VehicleId("v-001"),
                "京A12345", "VIN1234567890", "TS-001",
                "fleet-1", "2.0.0", 1,
                java.time.LocalDateTime.of(2026, 1, 1, 0, 0),
                java.time.LocalDateTime.of(2026, 1, 1, 0, 0));

        assertEquals("京A12345", v.licensePlate());
        assertEquals("VIN1234567890", v.vin());
        assertEquals("2.0.0", v.firmwareVersion().getVersionNumber());
        assertTrue(v.fleetId().isPresent());
        assertEquals("fleet-1", v.fleetId().get());
    }

    @Test
    @DisplayName("SafetyAlertEvent.reconstitute 正确重建告警事件")
    void safetyAlertEventReconstituteWorks() {
        com.aiot.domain.shared.AlertId alertId = new com.aiot.domain.shared.AlertId("alert-001");
        com.aiot.domain.shared.TripId tripId = new com.aiot.domain.shared.TripId("t-001");
        com.aiot.domain.shared.DriverId driverId = new com.aiot.domain.shared.DriverId("d-001");
        com.aiot.domain.shared.VehicleId vehicleId = new com.aiot.domain.shared.VehicleId("v-001");
        java.time.LocalDateTime occurred = java.time.LocalDateTime.of(2026, 7, 1, 9, 30);

        SafetyAlertEvent e = SafetyAlertEvent.reconstitute(alertId, tripId, driverId, vehicleId,
                com.aiot.domain.event.AlertType.FATIGUE, com.aiot.domain.event.RiskLevel.L2_WARNING,
                occurred, "司机出现疲劳迹象", false, null);

        assertEquals("alert-001", e.alertId().id());
        assertEquals(com.aiot.domain.event.AlertType.FATIGUE, e.alertType());
        assertEquals(com.aiot.domain.event.RiskLevel.L2_WARNING, e.riskLevel());
        assertEquals("司机出现疲劳迹象", e.alertMessage());
        assertFalse(e.isResolved());
    }

    @Test
    @DisplayName("RoadRageVoiceRecord.reconstitute 正确映射字段名差异")
    void roadRageVoiceRecordReconstituteMapsFieldNames() {
        com.aiot.domain.shared.RoadRageVoiceRecordId recordId =
                new com.aiot.domain.shared.RoadRageVoiceRecordId("rec-001");
        com.aiot.domain.shared.AlertId alertId = new com.aiot.domain.shared.AlertId("alert-001");
        java.time.LocalDateTime recordedAt = java.time.LocalDateTime.of(2026, 7, 1, 9, 0);
        java.time.LocalDateTime expiry = java.time.LocalDateTime.of(2026, 7, 31, 9, 0);

        RoadRageVoiceRecord r = RoadRageVoiceRecord.reconstitute(
                recordId, alertId, recordedAt, "/data/voice/encrypted.bin",
                true, expiry, true, 1,
                java.time.LocalDateTime.of(2026, 7, 1, 9, 0),
                java.time.LocalDateTime.of(2026, 7, 1, 9, 0));

        assertEquals("rec-001", r.recordId().id());
        assertEquals("/data/voice/encrypted.bin", r.encryptedAudioReference().get());
        assertTrue(r.isAnonymized());
        assertTrue(r.isSealed());
        assertEquals(expiry, r.retentionExpiresAt());
    }
}
