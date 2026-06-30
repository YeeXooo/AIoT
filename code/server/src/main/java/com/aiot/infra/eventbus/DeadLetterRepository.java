package com.aiot.infra.eventbus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * 死信表 Spring Data JPA Repository。
 * <p>
 * 设计依据：docs/ood_infrastructure.md §3.3.3
 * </p>
 */
@Repository
public interface DeadLetterRepository extends JpaRepository<DeadLetterEntity, String> {

    /**
     * 查询所有死信事件（按移入时间倒序）。
     */
    List<DeadLetterEntity> findAllByOrderByMovedAtDesc();

    /**
     * 按事件类型查询死信事件。
     */
    List<DeadLetterEntity> findByEventTypeOrderByMovedAtDesc(String eventType);

    /**
     * 查询指定时间之前的死信事件（用于清理）。
     */
    List<DeadLetterEntity> findByMovedAtBefore(Instant threshold);

    /**
     * 删除指定时间之前的死信事件（用于定期清理）。
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM DeadLetterEntity d WHERE d.movedAt < :threshold")
    int deleteByMovedAtBefore(@Param("threshold") Instant threshold);

    /**
     * 统计死信事件数量。
     */
    long countBy();
}
