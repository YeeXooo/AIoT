package com.aiot.domain.model;

import com.aiot.domain.event.RiskLevel;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import java.util.Collections;
import java.util.Set;

/**
 * 通知偏好（VO-18）
 * 家属订阅的风险等级集合，未配置时默认接收全部等级
 */
@Embeddable
@Getter
@EqualsAndHashCode
public final class NotificationPreference {
    private final Set<RiskLevel> subscribedLevels;

    private NotificationPreference(Set<RiskLevel> subscribedLevels) {
        this.subscribedLevels = subscribedLevels == null || subscribedLevels.isEmpty()
                ? Set.of(RiskLevel.values())
                : Collections.unmodifiableSet(subscribedLevels);
    }

    public static NotificationPreference of(Set<RiskLevel> levels) {
        return new NotificationPreference(levels);
    }

    public static NotificationPreference defaultAll() {
        return new NotificationPreference(Set.of(RiskLevel.values()));
    }

    public boolean shouldNotify(RiskLevel level) {
        return subscribedLevels.contains(level);
    }

    protected NotificationPreference() {
        this.subscribedLevels = Set.of(RiskLevel.values());
    }
}