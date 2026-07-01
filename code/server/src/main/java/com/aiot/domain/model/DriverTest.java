package com.aiot.domain.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Driver 驾驶员 POJO 测试")
class DriverTest {

    private Driver driver;

    @BeforeEach
    void setUp() {
        driver = new Driver();
    }

    @Nested
    @DisplayName("Getter/Setter 测试")
    class GetterSetter {

        @Test
        @DisplayName("driverId 读写")
        void shouldGetAndSetDriverId() {
            driver.setDriverId("DRV-001");
            assertEquals("DRV-001", driver.getDriverId());
        }

        @Test
        @DisplayName("name 读写")
        void shouldGetAndSetName() {
            driver.setName("张三");
            assertEquals("张三", driver.getName());
        }

        @Test
        @DisplayName("phone 读写")
        void shouldGetAndSetPhone() {
            driver.setPhone("13800138000");
            assertEquals("13800138000", driver.getPhone());
        }

        @Test
        @DisplayName("comprehensiveScore 读写")
        void shouldGetAndSetComprehensiveScore() {
            driver.setComprehensiveScore(85);
            assertEquals(85, driver.getComprehensiveScore());
        }

        @Test
        @DisplayName("createdAt 读写")
        void shouldGetAndSetCreatedAt() {
            LocalDateTime now = LocalDateTime.now();
            driver.setCreatedAt(now);
            assertEquals(now, driver.getCreatedAt());
        }
    }

    @Nested
    @DisplayName("初始状态测试")
    class InitialState {

        @Test
        @DisplayName("新建实例所有字段为 null")
        void allFieldsShouldBeNullByDefault() {
            Driver d = new Driver();
            assertNull(d.getDriverId());
            assertNull(d.getName());
            assertNull(d.getPhone());
            assertNull(d.getComprehensiveScore());
            assertNull(d.getCreatedAt());
        }
    }

    @Test
    @DisplayName("设置为 null 值")
    void shouldAllowNullValues() {
        driver.setName("张三");
        driver.setName(null);
        assertNull(driver.getName());
    }

    @Test
    @DisplayName("comprehensiveScore 可以设为 null")
    void shouldAllowNullScore() {
        driver.setComprehensiveScore(85);
        driver.setComprehensiveScore(null);
        assertNull(driver.getComprehensiveScore());
    }
}
