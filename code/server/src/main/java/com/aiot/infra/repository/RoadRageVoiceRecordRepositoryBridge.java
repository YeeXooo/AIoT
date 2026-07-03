package com.aiot.infra.repository;

import com.aiot.domain.model.RoadRageVoiceRecord;
import com.aiot.domain.repository.RoadRageVoiceRecordRepository;
import com.aiot.domain.shared.AlertId;
import com.aiot.domain.shared.RoadRageVoiceRecordId;
import com.aiot.infra.persistence.RoadRageVoiceRecordEntity;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@org.springframework.stereotype.Repository
public class RoadRageVoiceRecordRepositoryBridge implements RoadRageVoiceRecordRepository {

    private final RoadRageVoiceRecordJpaRepository jpaRepository;

    public RoadRageVoiceRecordRepositoryBridge(RoadRageVoiceRecordJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(RoadRageVoiceRecord record) {
        RoadRageVoiceRecordEntity entity = new RoadRageVoiceRecordEntity();
        entity.setRecordId(record.recordId().id());
        entity.setAlertId(record.alertId().id());
        entity.setStartedAt(record.recordedAt());
        entity.setEncryptedFilePath(record.encryptedAudioReference().orElse(null));
        entity.setExpiryTime(record.retentionExpiresAt());
        entity.setIsSealed(record.isSealed());
        jpaRepository.save(entity);
    }

    @Override
    public Optional<RoadRageVoiceRecord> findById(String id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<RoadRageVoiceRecord> findByAlertId(String alertId) {
        return jpaRepository.findAll().stream()
                .filter(e -> alertId.equals(e.getAlertId()))
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<RoadRageVoiceRecord> findAll() {
        return jpaRepository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<RoadRageVoiceRecord> findByExpiryBefore(Instant cutoff) {
        LocalDateTime cutoffDateTime = LocalDateTime.ofInstant(cutoff, ZoneId.systemDefault());
        return jpaRepository.findAll().stream()
                .filter(e -> e.getExpiryTime() != null && e.getExpiryTime().isBefore(cutoffDateTime))
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String id) {
        jpaRepository.deleteById(id);
    }

    private RoadRageVoiceRecord toDomain(RoadRageVoiceRecordEntity entity) {
        return RoadRageVoiceRecord.reconstitute(
                new RoadRageVoiceRecordId(entity.getRecordId()),
                new AlertId(entity.getAlertId()),
                entity.getStartedAt(),
                entity.getEncryptedFilePath(),
                false,
                entity.getExpiryTime(),
                Boolean.TRUE.equals(entity.getIsSealed()),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
