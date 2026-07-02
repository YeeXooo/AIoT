package com.aiot.domain.emergency;

import com.aiot.domain.event.DomainEventPublisher;
import com.aiot.domain.model.RoadRageVoiceRecord;
import com.aiot.domain.model.SensorReading;
import com.aiot.domain.repository.RoadRageVoiceRecordRepository;
import com.aiot.domain.shared.AlertId;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.Result;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PrivacyProtectionServiceImpl implements PrivacyProtectionService {

    private final RoadRageVoiceRecordRepository roadRageVoiceRecordRepository;
    private final DomainEventPublisher eventPublisher;

    public PrivacyProtectionServiceImpl(
            RoadRageVoiceRecordRepository roadRageVoiceRecordRepository,
            DomainEventPublisher eventPublisher) {
        this.roadRageVoiceRecordRepository = roadRageVoiceRecordRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Result<Void, AppError> validateDataDesensitization(SensorReading reading) {
        if (reading == null) {
            return Result.err(AppError.validationFailed("SensorReading must not be null"));
        }

        Map<String, Double> values = reading.values();
        if (!values.isEmpty()) {
            for (double feature : values.values()) {
                if (Double.isNaN(feature) || Double.isInfinite(feature)) {
                    return Result.err(AppError.validationFailed("Feature vector contains invalid values"));
                }
            }
        }

        return Result.ok(null);
    }

    @Override
    public Result<String, AppError> startVoiceRecording(AlertId alertId, DriverId driverId) {
        if (alertId == null) {
            return Result.err(AppError.validationFailed("alertId must not be null"));
        }
        if (driverId == null) {
            return Result.err(AppError.validationFailed("driverId must not be null"));
        }

        LocalDateTime now = LocalDateTime.now();
        RoadRageVoiceRecord record = RoadRageVoiceRecord.create(alertId, now);
        roadRageVoiceRecordRepository.save(record);

        return Result.ok(record.recordId().id());
    }

    @Override
    public Result<Void, AppError> sealVoiceRecord(String recordId) {
        Optional<RoadRageVoiceRecord> recordOpt = roadRageVoiceRecordRepository.findById(recordId);
        if (recordOpt.isEmpty()) {
            return Result.err(AppError.notFound("RoadRageVoiceRecord", recordId));
        }

        RoadRageVoiceRecord record = recordOpt.get();
        if (record.isSealed()) {
            return Result.err(AppError.invalidState("Record already sealed: " + recordId));
        }

        record.markAnonymized();
        record.seal();
        roadRageVoiceRecordRepository.save(record);

        return Result.ok(null);
    }

    @Override
    public Result<Integer, AppError> purgeExpiredRecords() {
        Instant now = Instant.now();
        List<RoadRageVoiceRecord> expiredRecords = roadRageVoiceRecordRepository.findByExpiryBefore(now);
        int purgedCount = 0;

        for (RoadRageVoiceRecord record : expiredRecords) {
            roadRageVoiceRecordRepository.delete(record.recordId().id());
            purgedCount++;
        }

        return Result.ok(purgedCount);
    }
}
