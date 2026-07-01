package com.aiot.infra.eventbus;

import com.aiot.domain.event.DomainEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Outbox 事件持久化器。
 * <p>
 * 负责将领域事件写入 domain_event_outbox 表。
 * 事件写入与聚合根保存在同一数据库事务中提交，保证"状态变更"与"事件已发布"的一致性。
 * </p>
 * <p>
 * 设计依据：docs/ood_infrastructure.md §3.3.1 OutboxPersister
 * </p>
 */
@Component
public class OutboxPersister {

    private static final Logger log = LoggerFactory.getLogger(OutboxPersister.class);

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxPersister(OutboxEventRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * 持久化领域事件到 outbox 表。
     * <p>
     * 此方法应在事务内调用（通常由 Application Service 或 @TransactionalEventListener 触发），
     * 确保事件写入与聚合根状态变更在同一事务中提交。
     * </p>
     *
     * @param event 领域事件
     * @param <E>   事件类型
     */
    @Transactional
    public <E extends DomainEvent> void persist(E event) {
        try {
            // 手动构建事件载荷 Map，避免 Jackson 对接口方法的序列化问题
            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("eventId", event.eventId());
            payloadMap.put("occurredAt", event.occurredAt().toString());
            payloadMap.put("aggregateId", event.aggregateId().value());
            payloadMap.put("aggregateType", event.aggregateId().aggregateType().name());

            String payload = objectMapper.writeValueAsString(payloadMap);
            String eventType = event.getClass().getSimpleName();

            OutboxEventEntity entity = new OutboxEventEntity(
                    event.eventId(),
                    eventType,
                    event.aggregateId().value(),
                    event.aggregateId().aggregateType().name(),
                    payload,
                    event.occurredAt()
            );

            outboxRepository.save(entity);
            log.debug("Persisted event to outbox: {} (eventId={})", eventType, event.eventId());

        } catch (Exception e) {
            log.error("Failed to serialize event {}: {}", event.getClass().getSimpleName(), e.getMessage(), e);
            throw new RuntimeException("Event serialization failed", e);
        }
    }

    /**
     * 批量持久化领域事件到 outbox 表。
     *
     * @param events 领域事件列表
     */
    @Transactional
    public void persistAll(Iterable<? extends DomainEvent> events) {
        for (DomainEvent event : events) {
            persist(event);
        }
    }
}
