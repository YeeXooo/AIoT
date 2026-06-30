package com.aiot.infra.repository;

import com.aiot.infra.persistence.TrajectoryProjectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrajectoryProjectionJpaRepository extends JpaRepository<TrajectoryProjectionEntity, String> {

    List<TrajectoryProjectionEntity> findByTripIdOrderByRecordedAtAsc(String tripId);
}
