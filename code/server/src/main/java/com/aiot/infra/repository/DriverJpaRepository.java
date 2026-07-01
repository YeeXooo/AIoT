package com.aiot.infra.repository;

import com.aiot.infra.persistence.DriverJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DriverJpaRepository extends JpaRepository<DriverJpaEntity, String> {

    @Query("SELECT d FROM DriverJpaEntity d WHERE d.name LIKE %?1%")
    List<DriverJpaEntity> findByNameLike(String keyword);
}
