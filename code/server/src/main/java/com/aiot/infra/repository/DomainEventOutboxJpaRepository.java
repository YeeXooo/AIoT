package com.aiot.infra.repository;

import com.aiot.infra.persistence.DomainEventOutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DomainEventOutboxJpaRepository extends JpaRepository<DomainEventOutboxEntity, String> {

    @Query("SELECT e FROM DomainEventOutboxEntity e WHERE e.published = false ORDER BY e.createdAt ASC")
    List<DomainEventOutboxEntity> findUnpublished();
}
