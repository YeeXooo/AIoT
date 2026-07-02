package com.aiot.domain.repository;

import com.aiot.domain.model.RoadRageVoiceRecord;

import java.util.List;
import java.util.Optional;

public interface RoadRageVoiceRecordRepository {
    void save(RoadRageVoiceRecord record);
    Optional<RoadRageVoiceRecord> findById(String id);
    List<RoadRageVoiceRecord> findByAlertId(String alertId);
    List<RoadRageVoiceRecord> findAll();
    void delete(String id);
}
