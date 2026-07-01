package com.aiot.infra.eventbus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Outbox 事件表 Spring Data JPA Repository。
 * <p>
 * 设计依据：docs/ood_infrastructure.md §3.3.2 投递器轮询索引
 * </p>
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, String> {

    /**
     * 查询待投递事件。
     * <p>
     * 筛选条件：
     * 1. published = FALSE
     * 2. last_attempt_at IS NULL（新事件）或 last_attempt_at < NOW() - 60s（退避等待期已过）
     * </p>
     */
    @Query("""
        SELECT e FROM OutboxEventEntity e
        WHERE e.published = FALSE
          AND (e.lastAttemptAt IS NULL OR e.lastAttemptAt < :cutoff)
        ORDER BY e.lastAttemptAt ASC NULLS FIRST
        LIMIT :limit
    """)
    List<OutboxEventEntity> findPendingEvents(
            @Param("cutoff") Instant cutoff,
            @Param("limit") int limit);

    /**
     * 统计未投递事件数量（用于积压监控）。
     */
    long countByPublishedFalse();

    /**
     * 查询超过指定时间未投递的事件数量（用于积压告警）。
     */
    @Query("""
        SELECT COUNT(e) FROM OutboxEventEntity e
        WHERE e.published = FALSE AND e.createdAt < :threshold
    """)
    long countStaleEvents(@Param("threshold") Instant threshold);
}
