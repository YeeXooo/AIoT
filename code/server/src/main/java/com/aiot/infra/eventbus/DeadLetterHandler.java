package com.aiot.infra.eventbus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 死信事件处理器。
 * <p>
 * 负责将超限事件移入死信表，并提供定期清理功能。
 * </p>
 * <p>
 * 设计依据：docs/ood_infrastructure.md §3.3.3, §3.3.5
 * </p>
 */
@Component
public class DeadLetterHandler {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterHandler.class);

    /**
     * 死信保留天数。
     */
    private static final int RETENTION_DAYS = 30;

    private final DeadLetterRepository deadLetterRepository;

    public DeadLetterHandler(DeadLetterRepository deadLetterRepository) {
        this.deadLetterRepository = deadLetterRepository;
    }

    /**
     * 将 outbox 事件移入死信表。
     *
     * @param outboxEvent 超限的 outbox 事件
     */
    @Transactional
    public void moveToDeadLetter(OutboxEventEntity outboxEvent) {
        DeadLetterEntity deadLetter = new DeadLetterEntity(
                outboxEvent.getEventId(),
                outboxEvent.getEventType(),
                outboxEvent.getAggregateId(),
                outboxEvent.getPayload(),
                outboxEvent.getOccurredAt(),
                outboxEvent.getRetryCount(),
                outboxEvent.getLastError() != null ? outboxEvent.getLastError() : "Unknown error"
        );

        deadLetterRepository.save(deadLetter);
        log.warn("Event moved to DLQ: {} (eventId={}, retryCount={})",
                outboxEvent.getEventType(), outboxEvent.getEventId(), outboxEvent.getRetryCount());
    }

    /**
     * 定期清理过期死信事件。
     * <p>
     * 每日凌晨 3:00 执行，删除 30 天前的死信记录。
     * </p>
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupExpiredDeadLetters() {
        Instant threshold = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);

        List<DeadLetterEntity> expiredEvents = deadLetterRepository.findByMovedAtBefore(threshold);

        if (expiredEvents.isEmpty()) {
            return;
        }

        log.info("Cleaning up {} expired dead letter events (older than {} days)",
                expiredEvents.size(), RETENTION_DAYS);

        int deletedCount = deadLetterRepository.deleteByMovedAtBefore(threshold);
        log.info("Deleted {} expired dead letter events", deletedCount);
    }

    /**
     * 查询所有死信事件（用于运维查看）。
     */
    @Transactional(readOnly = true)
    public List<DeadLetterEntity> getAllDeadLetters() {
        return deadLetterRepository.findAllByOrderByMovedAtDesc();
    }

    /**
     * 按事件类型查询死信事件。
     */
    @Transactional(readOnly = true)
    public List<DeadLetterEntity> getDeadLettersByEventType(String eventType) {
        return deadLetterRepository.findByEventTypeOrderByMovedAtDesc(eventType);
    }

    /**
     * 统计死信事件数量。
     */
    @Transactional(readOnly = true)
    public long countDeadLetters() {
        return deadLetterRepository.countBy();
    }
}
