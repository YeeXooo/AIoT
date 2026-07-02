package com.aiot.domain.model;

import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * 驾驶员覆盖信号（VO-21）
 * 驾驶员主动接管操作，用于判断是否中止干预升级
 */
@Embeddable
@Getter
@EqualsAndHashCode
public final class OverrideSignal {
    private final OverrideType type;
    private final Instant timestamp;

    private OverrideSignal(OverrideType type, Instant timestamp) {
        if (type == null) { throw new IllegalArgumentException("操作类型不能为空"); }
        if (timestamp == null) { throw new IllegalArgumentException("时间戳不能为空"); }
        this.timestamp = timestamp;
        this.type = type;
    }

    public static OverrideSignal of(OverrideType type, Instant timestamp) {
        return new OverrideSignal(type, timestamp);
    }

    protected OverrideSignal() {
        this.type = null;
        this.timestamp = Instant.EPOCH;
    }
}