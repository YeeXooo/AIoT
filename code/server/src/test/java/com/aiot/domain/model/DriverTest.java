package com.aiot.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Driver 聚合根")
class DriverTest {

    @Nested @DisplayName("工厂方法 create")
    class Create {
        @Test void createsDriverWithValidInputs() {
            Driver d = Driver.create("张三", "13800000001");
            assertNotNull(d.driverId());
            assertEquals("张三", d.name());
            assertEquals("13800000001", d.phone());
            assertTrue(d.isActive());
            assertNotNull(d.createdAt());
            assertEquals(1, d.version());
        }

        @Test void nullNameThrows() {
            assertThrows(NullPointerException.class, () -> Driver.create(null, "138"));
        }

        @Test void blankNameThrows() {
            assertThrows(IllegalArgumentException.class, () -> Driver.create("  ", "138"));
        }

        @Test void nullPhoneThrows() {
            assertThrows(NullPointerException.class, () -> Driver.create("张三", null));
        }

        @Test void blankPhoneThrows() {
            assertThrows(IllegalArgumentException.class, () -> Driver.create("张三", "  "));
        }
    }

    @Nested @DisplayName("reconstitute 重建")
    class Reconstitute {
        @Test void reconstituteFromPersistence() {
            Driver d = Driver.create("李四", "13900000001");
            Driver r = Driver.reconstitute(d.driverId(), "李四", "13900000001", 85, 3,
                    d.createdAt(), d.updatedAt());
            assertEquals(d.driverId(), r.driverId());
            assertEquals("李四", r.name());
            assertEquals(85, r.comprehensiveScore().orElseThrow().getValue());
            assertEquals(3, r.version());
        }

        @Test void reconstituteWithNullScore() {
            Driver d = Driver.create("王五", "13000000001");
            Driver r = Driver.reconstitute(d.driverId(), "王五", "13000000001", null, 1,
                    d.createdAt(), d.updatedAt());
            assertTrue(r.comprehensiveScore().isEmpty());
        }
    }

    @Nested @DisplayName("状态变更")
    class Mutation {
        @Test void updateName() {
            Driver d = Driver.create("赵六", "13100000001");
            d.updateName("赵六六");
            assertEquals("赵六六", d.name());
        }

        @Test void updatePhone() {
            Driver d = Driver.create("孙七", "13200000001");
            d.updatePhone("13200000002");
            assertEquals("13200000002", d.phone());
        }

        @Test void updateComprehensiveScore() {
            Driver d = Driver.create("周八", "13300000001");
            d.updateComprehensiveScore(DriverComprehensiveScore.of(75));
            assertEquals(75, d.comprehensiveScore().orElseThrow().getValue());
        }

        @Test void deactivate() {
            Driver d = Driver.create("待停用", "13400000001");
            d.deactivate();
            assertFalse(d.isActive());
        }

        @Test void validatePassesForValidDriver() {
            Driver d = Driver.create("正常", "13500000001");
            assertDoesNotThrow(d::validate);
        }
    }

    @Nested @DisplayName("相等性")
    class Equality {
        @Test void sameDriverIdMeansEqual() {
            Driver d1 = Driver.create("相等测试", "13600000001");
            Driver d2 = Driver.reconstitute(d1.driverId(), "相等测试", "13600000001", 90, 2,
                    d1.createdAt(), d1.updatedAt());
            assertEquals(d1, d2);
        }

        @Test void differentDriverIdNotEqual() {
            Driver d1 = Driver.create("A", "111");
            Driver d2 = Driver.create("B", "222");
            assertNotEquals(d1, d2);
        }
    }
}
