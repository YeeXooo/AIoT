package com.aiot.domain.event;

import com.aiot.domain.shared.AggregateId;

import java.time.Instant;

/**
 * 领域事件公共接口（marker interface）。
 * 所有领域事件类型在实现时实现该接口。
 * 使用 record 风格的访问器方法名（不带 get 前缀）。
 */
public interface DomainEvent {

    /**
     * 事件唯一标识（UUID），供事件总线进行幂等去重与审计追踪。
     */
    String eventId();

    /**
     * 事件发生时间戳（UTC），记录业务事实的发生时刻，非事件持久化时刻。
     */
    Instant occurredAt();

    /**
     * 关联的聚合根标识，标识此事件源于哪个聚合根的状态变更。
     */
    AggregateId aggregateId();
}
