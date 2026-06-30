package com.aiot.infra.repository;

import com.aiot.infra.persistence.PhysiologicalSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PhysiologicalSnapshotJpaRepository extends JpaRepository<PhysiologicalSnapshotEntity, String> {

    List<PhysiologicalSnapshotEntity> findByTripIdOrderByTimestampAsc(String tripId);
}
