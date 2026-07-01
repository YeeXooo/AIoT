package com.aiot.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NotificationPreference 通知偏好测试")
class NotificationPreferenceTest {

    @Nested
    @DisplayName("of() 工厂方法")
    class OfMethod {

        @Test
        @DisplayName("指定订阅等级")
        void shouldCreateWithSpecificLevels() {
            Set<RiskLevel> levels = EnumSet.of(RiskLevel.HIGH, RiskLevel.CRITICAL);
            NotificationPreference pref = NotificationPreference.of(levels);
            assertTrue(pref.getSubscribedLevels().contains(RiskLevel.HIGH));
            assertTrue(pref.getSubscribedLevels().contains(RiskLevel.CRITICAL));
            assertFalse(pref.getSubscribedLevels().contains(RiskLevel.LOW));
            assertEquals(2, pref.getSubscribedLevels().size());
        }

        @Test
        @DisplayName("null 集合默认订阅全部")
        void shouldDefaultToAllWhenNull() {
            NotificationPreference pref = NotificationPreference.of(null);
            assertEquals(Set.of(RiskLevel.values()).size(), pref.getSubscribedLevels().size());
        }

        @Test
        @DisplayName("空集合默认订阅全部")
        void shouldDefaultToAllWhenEmpty() {
            NotificationPreference pref = NotificationPreference.of(Set.of());
            assertEquals(Set.of(RiskLevel.values()).size(), pref.getSubscribedLevels().size());
        }

        @Test
        @DisplayName("集合不可变")
        void shouldReturnUnmodifiableSet() {
            Set<RiskLevel> levels = EnumSet.of(RiskLevel.HIGH);
            NotificationPreference pref = NotificationPreference.of(levels);
            assertThrows(UnsupportedOperationException.class, () ->
                    pref.getSubscribedLevels().add(RiskLevel.LOW));
        }
    }

    @Nested
    @DisplayName("defaultAll() 默认全部")
    class DefaultAllMethod {

        @Test
        @DisplayName("订阅全部等级")
        void shouldSubscribeAllLevels() {
            NotificationPreference pref = NotificationPreference.defaultAll();
            for (RiskLevel level : RiskLevel.values()) {
                assertTrue(pref.getSubscribedLevels().contains(level));
            }
        }
    }

    @Nested
    @DisplayName("shouldNotify() 通知判定")
    class ShouldNotifyMethod {

        @Test
        @DisplayName("订阅等级应通知")
        void shouldNotifyForSubscribedLevel() {
            NotificationPreference pref = NotificationPreference.of(EnumSet.of(RiskLevel.HIGH));
            assertTrue(pref.shouldNotify(RiskLevel.HIGH));
        }

        @Test
        @DisplayName("未订阅等级不通知")
        void shouldNotNotifyForUnsubscribedLevel() {
            NotificationPreference pref = NotificationPreference.of(EnumSet.of(RiskLevel.HIGH));
            assertFalse(pref.shouldNotify(RiskLevel.LOW));
        }

        @Test
        @DisplayName("默认全部时所有等级都通知")
        void shouldNotifyAllForDefault() {
            NotificationPreference pref = NotificationPreference.defaultAll();
            assertTrue(pref.shouldNotify(RiskLevel.LOW));
            assertTrue(pref.shouldNotify(RiskLevel.MEDIUM));
            assertTrue(pref.shouldNotify(RiskLevel.HIGH));
            assertTrue(pref.shouldNotify(RiskLevel.CRITICAL));
        }
    }

    @Nested
    @DisplayName("equals/hashCode")
    class EqualsHashCode {

        @Test
        @DisplayName("相同订阅集合相等")
        void shouldBeEqual() {
            NotificationPreference a = NotificationPreference.of(EnumSet.of(RiskLevel.HIGH));
            NotificationPreference b = NotificationPreference.of(EnumSet.of(RiskLevel.HIGH));
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("不同订阅集合不等")
        void shouldNotBeEqual() {
            NotificationPreference a = NotificationPreference.of(EnumSet.of(RiskLevel.HIGH));
            NotificationPreference b = NotificationPreference.of(EnumSet.of(RiskLevel.LOW));
            assertNotEquals(a, b);
        }
    }
}
