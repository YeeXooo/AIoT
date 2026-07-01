package com.aiot.infra.repository;

import com.aiot.infra.persistence.DriverHealthProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DriverHealthProfileJpaRepository extends JpaRepository<DriverHealthProfileEntity, String> {
}
