package com.aiot.infra.repository;

import com.aiot.infra.persistence.DomainEventDlqEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DomainEventDlqJpaRepository extends JpaRepository<DomainEventDlqEntity, String> {
}
