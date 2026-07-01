package com.aiot.domain.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AlertEvent 告警事件 POJO 测试")
class AlertEventTest {

    private AlertEvent alertEvent;

    @BeforeEach
    void setUp() {
        alertEvent = new AlertEvent();
    }

    @Nested
    @DisplayName("Getter/Setter 测试")
    class GetterSetter {

        @Test
        @DisplayName("alertId 读写")
        void shouldGetAndSetAlertId() {
            alertEvent.setAlertId("ALT-001");
            assertEquals("ALT-001", alertEvent.getAlertId());
        }

        @Test
        @DisplayName("tripId 读写")
        void shouldGetAndSetTripId() {
            alertEvent.setTripId("TRIP-001");
            assertEquals("TRIP-001", alertEvent.getTripId());
        }

        @Test
        @DisplayName("driverId 读写")
        void shouldGetAndSetDriverId() {
            alertEvent.setDriverId("DRV-001");
            assertEquals("DRV-001", alertEvent.getDriverId());
        }

        @Test
        @DisplayName("vehicleId 读写")
        void shouldGetAndSetVehicleId() {
            alertEvent.setVehicleId("VH-001");
            assertEquals("VH-001", alertEvent.getVehicleId());
        }

        @Test
        @DisplayName("alertType 读写")
        void shouldGetAndSetAlertType() {
            alertEvent.setAlertType("FATIGUE");
            assertEquals("FATIGUE", alertEvent.getAlertType());
        }

        @Test
        @DisplayName("riskLevel 读写")
        void shouldGetAndSetRiskLevel() {
            alertEvent.setRiskLevel("HIGH");
            assertEquals("HIGH", alertEvent.getRiskLevel());
        }

        @Test
        @DisplayName("occurredAt 读写")
        void shouldGetAndSetOccurredAt() {
            LocalDateTime now = LocalDateTime.now();
            alertEvent.setOccurredAt(now);
            assertEquals(now, alertEvent.getOccurredAt());
        }

        @Test
        @DisplayName("alertMsg 读写")
        void shouldGetAndSetAlertMsg() {
            alertEvent.setAlertMsg("疲劳驾驶告警");
            assertEquals("疲劳驾驶告警", alertEvent.getAlertMsg());
        }
    }

    @Nested
    @DisplayName("初始状态测试")
    class InitialState {

        @Test
        @DisplayName("新建实例所有字段为 null")
        void allFieldsShouldBeNullByDefault() {
            AlertEvent event = new AlertEvent();
            assertNull(event.getAlertId());
            assertNull(event.getTripId());
            assertNull(event.getDriverId());
            assertNull(event.getVehicleId());
            assertNull(event.getAlertType());
            assertNull(event.getRiskLevel());
            assertNull(event.getOccurredAt());
            assertNull(event.getAlertMsg());
        }
    }

    @Test
    @DisplayName("设置为 null 值")
    void shouldAllowNullValues() {
        alertEvent.setAlertId("ALT-001");
        alertEvent.setAlertId(null);
        assertNull(alertEvent.getAlertId());
    }
}
