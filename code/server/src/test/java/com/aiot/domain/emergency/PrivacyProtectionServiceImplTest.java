package com.aiot.domain.emergency;

import com.aiot.domain.event.DomainEventPublisher;
import com.aiot.domain.model.RoadRageVoiceRecord;
import com.aiot.domain.model.SensorReading;
import com.aiot.domain.repository.RoadRageVoiceRecordRepository;
import com.aiot.domain.shared.AlertId;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.TripId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrivacyProtectionServiceImplTest {

    @Mock private RoadRageVoiceRecordRepository roadRageVoiceRecordRepository;
    @Mock private DomainEventPublisher eventPublisher;

    private PrivacyProtectionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PrivacyProtectionServiceImpl(roadRageVoiceRecordRepository, eventPublisher);
    }

    @Test
    void validateDataDesensitizationReturnsOkForValidReading() {
        SensorReading reading = new SensorReading(
                SensorReading.SensorType.DMS_CAMERA,
                Instant.now(),
                TripId.generate(),
                Map.of("feature1", 0.5)
        );

        Result<Void, AppError> result = service.validateDataDesensitization(reading);

        assertTrue(result.isOk());
    }

    @Test
    void validateDataDesensitizationReturnsOkForEmptyValues() {
        SensorReading reading = new SensorReading(
                SensorReading.SensorType.DMS_CAMERA,
                Instant.now(),
                TripId.generate(),
                Collections.emptyMap()
        );

        Result<Void, AppError> result = service.validateDataDesensitization(reading);

        assertTrue(result.isOk());
    }

    @Test
    void validateDataDesensitizationReturnsErrorForNullReading() {
        Result<Void, AppError> result = service.validateDataDesensitization(null);

        assertTrue(result.isErr());
        assertEquals("ValidationFailed", result.unwrapErr().code());
    }

    @Test
    void validateDataDesensitizationReturnsErrorForNaNValue() {
        SensorReading reading = new SensorReading(
                SensorReading.SensorType.DMS_CAMERA,
                Instant.now(),
                TripId.generate(),
                Map.of("feature1", Double.NaN)
        );

        Result<Void, AppError> result = service.validateDataDesensitization(reading);

        assertTrue(result.isErr());
        assertEquals("ValidationFailed", result.unwrapErr().code());
    }

    @Test
    void validateDataDesensitizationReturnsErrorForInfiniteValue() {
        SensorReading reading = new SensorReading(
                SensorReading.SensorType.DMS_CAMERA,
                Instant.now(),
                TripId.generate(),
                Map.of("feature1", Double.POSITIVE_INFINITY)
        );

        Result<Void, AppError> result = service.validateDataDesensitization(reading);

        assertTrue(result.isErr());
        assertEquals("ValidationFailed", result.unwrapErr().code());
    }

    @Test
    void startVoiceRecordingSavesRecordAndReturnsRecordId() {
        AlertId alertId = AlertId.generate();
        DriverId driverId = DriverId.generate();

        Result<String, AppError> result = service.startVoiceRecording(alertId, driverId);

        assertTrue(result.isOk());
        assertNotNull(result.unwrap());
        verify(roadRageVoiceRecordRepository).save(any(RoadRageVoiceRecord.class));
    }

    @Test
    void startVoiceRecordingReturnsErrorForNullAlertId() {
        DriverId driverId = DriverId.generate();

        Result<String, AppError> result = service.startVoiceRecording(null, driverId);

        assertTrue(result.isErr());
        assertEquals("ValidationFailed", result.unwrapErr().code());
    }

    @Test
    void startVoiceRecordingReturnsErrorForNullDriverId() {
        AlertId alertId = AlertId.generate();

        Result<String, AppError> result = service.startVoiceRecording(alertId, null);

        assertTrue(result.isErr());
        assertEquals("ValidationFailed", result.unwrapErr().code());
    }

    @Test
    void sealVoiceRecordSealsAndSavesRecord() {
        AlertId alertId = AlertId.generate();
        RoadRageVoiceRecord record = RoadRageVoiceRecord.create(alertId, java.time.LocalDateTime.now());
        when(roadRageVoiceRecordRepository.findById(record.recordId().id())).thenReturn(Optional.of(record));

        Result<Void, AppError> result = service.sealVoiceRecord(record.recordId().id());

        assertTrue(result.isOk());
        assertTrue(record.isSealed());
        verify(roadRageVoiceRecordRepository).save(record);
    }

    @Test
    void sealVoiceRecordReturnsErrorForMissingRecord() {
        when(roadRageVoiceRecordRepository.findById("nonexistent")).thenReturn(Optional.empty());

        Result<Void, AppError> result = service.sealVoiceRecord("nonexistent");

        assertTrue(result.isErr());
        assertEquals("NotFound", result.unwrapErr().code());
    }

    @Test
    void sealVoiceRecordReturnsErrorForAlreadySealedRecord() {
        AlertId alertId = AlertId.generate();
        RoadRageVoiceRecord record = RoadRageVoiceRecord.create(alertId, java.time.LocalDateTime.now());
        record.markAnonymized();
        record.seal();
        when(roadRageVoiceRecordRepository.findById(record.recordId().id())).thenReturn(Optional.of(record));

        Result<Void, AppError> result = service.sealVoiceRecord(record.recordId().id());

        assertTrue(result.isErr());
        assertEquals("InvalidState", result.unwrapErr().code());
    }

    @Test
    void purgeExpiredRecordsDeletesExpiredRecordsAndReturnsCount() {
        AlertId alertId = AlertId.generate();
        RoadRageVoiceRecord expiredRecord = RoadRageVoiceRecord.create(alertId, java.time.LocalDateTime.now().minusDays(60));
        when(roadRageVoiceRecordRepository.findByExpiryBefore(any(Instant.class))).thenReturn(List.of(expiredRecord));

        Result<Integer, AppError> result = service.purgeExpiredRecords();

        assertTrue(result.isOk());
        assertEquals(1, result.unwrap());
        verify(roadRageVoiceRecordRepository).delete(expiredRecord.recordId().id());
    }

    @Test
    void purgeExpiredRecordsReturnsZeroWhenNoExpiredRecords() {
        when(roadRageVoiceRecordRepository.findByExpiryBefore(any(Instant.class))).thenReturn(Collections.emptyList());

        Result<Integer, AppError> result = service.purgeExpiredRecords();

        assertTrue(result.isOk());
        assertEquals(0, result.unwrap());
        verify(roadRageVoiceRecordRepository, never()).delete(any());
    }
}
