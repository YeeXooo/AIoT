package com.aiot.domain.shared;

import java.util.Objects;
import java.util.UUID;

/**
 * 聚合根标识值对象。
 * 封装聚合根标识的字面值及其聚合类型枚举，使事件消费方可在编译期区分不同聚合根标识类型。
 */
public record AggregateId(String value, AggregateType aggregateType) {

    public AggregateId {
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(aggregateType, "aggregateType must not be null");
    }

    public static AggregateId generate(AggregateType aggregateType) {
        return new AggregateId(UUID.randomUUID().toString(), aggregateType);
    }

    @Override
    public String toString() {
        return value;
    }
}
