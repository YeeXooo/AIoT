package com.aiot.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GeoLocation 地理位置 Record 测试")
class GeoLocationTest {

    @Nested
    @DisplayName("正常创建")
    class ValidCreation {

        @Test
        @DisplayName("创建北京坐标")
        void shouldCreateBeijingLocation() {
            GeoLocation loc = new GeoLocation(39.9042, 116.4074);
            assertEquals(39.9042, loc.latitude());
            assertEquals(116.4074, loc.longitude());
        }

        @Test
        @DisplayName("创建零点坐标")
        void shouldCreateZeroLocation() {
            GeoLocation loc = new GeoLocation(0.0, 0.0);
            assertEquals(0.0, loc.latitude());
            assertEquals(0.0, loc.longitude());
        }

        @Test
        @DisplayName("边界值 - 纬度极值")
        void shouldAcceptBoundaryLatitudes() {
            GeoLocation north = new GeoLocation(90.0, 0.0);
            GeoLocation south = new GeoLocation(-90.0, 0.0);
            assertEquals(90.0, north.latitude());
            assertEquals(-90.0, south.latitude());
        }

        @Test
        @DisplayName("边界值 - 经度极值")
        void shouldAcceptBoundaryLongitudes() {
            GeoLocation east = new GeoLocation(0.0, 180.0);
            GeoLocation west = new GeoLocation(0.0, -180.0);
            assertEquals(180.0, east.longitude());
            assertEquals(-180.0, west.longitude());
        }
    }

    @Nested
    @DisplayName("非法参数校验")
    class InvalidParameters {

        @ParameterizedTest
        @ValueSource(doubles = {90.1, -90.1, 100.0, -100.0, 180.0})
        @DisplayName("纬度超范围抛出 IllegalArgumentException")
        void shouldThrowForInvalidLatitude(double lat) {
            assertThrows(IllegalArgumentException.class, () -> new GeoLocation(lat, 0.0));
        }

        @ParameterizedTest
        @ValueSource(doubles = {180.1, -180.1, 200.0, -200.0})
        @DisplayName("经度超范围抛出 IllegalArgumentException")
        void shouldThrowForInvalidLongitude(double lng) {
            assertThrows(IllegalArgumentException.class, () -> new GeoLocation(0.0, lng));
        }
    }

    @Nested
    @DisplayName("Record 特性")
    class RecordFeatures {

        @Test
        @DisplayName("equals 相等")
        void shouldBeEqual() {
            GeoLocation a = new GeoLocation(39.9, 116.4);
            GeoLocation b = new GeoLocation(39.9, 116.4);
            assertEquals(a, b);
        }

        @Test
        @DisplayName("equals 不等")
        void shouldNotBeEqual() {
            GeoLocation a = new GeoLocation(39.9, 116.4);
            GeoLocation b = new GeoLocation(31.2, 121.5);
            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("hashCode 一致")
        void shouldHaveSameHashCode() {
            GeoLocation a = new GeoLocation(39.9, 116.4);
            GeoLocation b = new GeoLocation(39.9, 116.4);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("toString 包含字段值")
        void shouldHaveToString() {
            GeoLocation loc = new GeoLocation(39.9, 116.4);
            assertTrue(loc.toString().contains("39.9"));
            assertTrue(loc.toString().contains("116.4"));
        }
    }
}
