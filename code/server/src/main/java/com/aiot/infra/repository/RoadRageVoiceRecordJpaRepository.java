package com.aiot.infra.repository;

import com.aiot.domain.model.RoadRageVoiceRecord;
import com.aiot.domain.repository.RoadRageVoiceRecordRepository;
import com.aiot.infra.persistence.RoadRageVoiceRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoadRageVoiceRecordJpaRepository extends JpaRepository<RoadRageVoiceRecordEntity, String> {

    List<RoadRageVoiceRecordEntity> findByDriverId(String driverId);

    List<RoadRageVoiceRecordEntity> findByIsSealedFalse();
}
