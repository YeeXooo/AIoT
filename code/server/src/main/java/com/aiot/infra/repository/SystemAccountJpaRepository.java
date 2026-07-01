package com.aiot.infra.repository;

import com.aiot.infra.persistence.SystemAccountJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SystemAccountJpaRepository extends JpaRepository<SystemAccountJpaEntity, String> {

    Optional<SystemAccountJpaEntity> findByPhone(String phone);
}
