package com.aiot.infra.eventbus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Outbox 事件投递器。
 * <p>
 * 定时轮询 domain_event_outbox 表，将待投递事件发送至消息队列。
 * 支持指数退避重试，超限事件移入死信表。
 * </p>
 * <p>
 * 设计依据：docs/ood_infrastructure.md §3.3.5 Outbox 投递器重试逻辑
 * </p>
 */
@Component
public class OutboxRelayer {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayer.class);

    /**
     * 最大重试次数。
     */
    private static final int MAX_RETRY_COUNT = 10;

    /**
     * 退避基础上界（秒）。
     */
    private static final long MAX_BACKOFF_SECONDS = 60;

    /**
     * 每次轮询获取的最大事件数。
     */
    private static final int POLL_LIMIT = 100;

    private final OutboxEventRepository outboxRepository;
    private final DeadLetterHandler deadLetterHandler;
    private final EventPublisher eventPublisher;

    public OutboxRelayer(OutboxEventRepository outboxRepository,
                         DeadLetterHandler deadLetterHandler,
                         EventPublisher eventPublisher) {
        this.outboxRepository = outboxRepository;
        this.deadLetterHandler = deadLetterHandler;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 定时轮询 outbox 表，投递待处理事件。
     */
    @Scheduled(fixedDelay = 5000)
    public void pollAndDeliver() {
        // SQL 层过滤：使用 60s 上界
        Instant cutoff = Instant.now().minusSeconds(MAX_BACKOFF_SECONDS);
        List<OutboxEventEntity> pendingEvents = outboxRepository.findPendingEvents(cutoff, POLL_LIMIT);

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Found {} pending events to process", pendingEvents.size());

        for (OutboxEventEntity event : pendingEvents) {
            processEvent(event);
        }
    }

    /**
     * 处理单个事件。
     */
    private void processEvent(OutboxEventEntity event) {
        // Java 层过滤：计算精确退避间隔
        if (!shouldAttemptDelivery(event)) {
            return;
        }

        // 检查是否超过最大重试次数
        if (event.getRetryCount() >= MAX_RETRY_COUNT) {
            log.warn("Event {} exceeded max retry count ({}), moving to DLQ",
                    event.getEventId(), MAX_RETRY_COUNT);
            moveToDeadLetter(event);
            return;
        }

        // 尝试投递
        try {
            eventPublisher.publish(event.getEventType(), event.getPayload());
            event.markPublished();
            outboxRepository.save(event);
            log.info("Event delivered successfully: {} (eventId={})", event.getEventType(), event.getEventId());
        } catch (Exception e) {
            handleDeliveryFailure(event, e);
        }
    }

    /**
     * 判断是否应该尝试投递（精确退避间隔计算）。
     */
    private boolean shouldAttemptDelivery(OutboxEventEntity event) {
        if (event.getLastAttemptAt() == null) {
            return true;
        }

        long backoffSeconds = calculateBackoffSeconds(event.getRetryCount());
        Instant nextAttemptTime = event.getLastAttemptAt().plusSeconds(backoffSeconds);

        return Instant.now().isAfter(nextAttemptTime);
    }

    /**
     * 计算退避间隔（秒）。
     * <p>
     * 公式：min(2^retryCount × 1s, 60s)
     * </p>
     */
    private long calculateBackoffSeconds(int retryCount) {
        if (retryCount <= 0) {
            return 1;
        }
        return Math.min(1L << retryCount, MAX_BACKOFF_SECONDS);
    }

    /**
     * 处理投递失败。
     */
    private void handleDeliveryFailure(OutboxEventEntity event, Exception e) {
        String errorMessage = e.getMessage();
        event.markFailed(errorMessage);
        outboxRepository.save(event);

        long backoffSeconds = calculateBackoffSeconds(event.getRetryCount());
        log.warn("Event delivery failed: {} (eventId={}, retryCount={}, nextRetryIn={}s)",
                event.getEventType(), event.getEventId(), event.getRetryCount(), backoffSeconds);
    }

    /**
     * 将事件移入死信表。
     */
    private void moveToDeadLetter(OutboxEventEntity event) {
        deadLetterHandler.moveToDeadLetter(event);
        outboxRepository.delete(event);
        log.warn("Event moved to DLQ: {} (eventId={})", event.getEventType(), event.getEventId());
    }

    /**
     * 事件发布者接口。
     */
    public interface EventPublisher {
        /**
         * 发布事件到消息队列。
         *
         * @param eventType 事件类型
         * @param payload   事件载荷（JSON）
         */
        void publish(String eventType, String payload);
    }

    /**
     * 默认事件发布者（日志记录，用于测试和本地开发）。
     */
    @org.springframework.stereotype.Component
    public static class LoggingEventPublisher implements EventPublisher {
        private static final Logger log = LoggerFactory.getLogger(LoggingEventPublisher.class);

        @Override
        public void publish(String eventType, String payload) {
            log.info("Publishing event to message queue: {} payload={}", eventType, payload);
        }
    }
}
