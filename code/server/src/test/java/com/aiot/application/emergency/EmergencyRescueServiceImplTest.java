package com.aiot.application.emergency;

import com.aiot.domain.emergency.EmergencyRescueService;
import com.aiot.domain.model.RescueAuthorizationToken;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.RescueReportId;
import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.VehicleId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmergencyRescueServiceImplTest {

    @Mock
    private EmergencyRescueService emergencyRescueService;

    private EmergencyRescueServiceImpl service;

    private static final DriverId DRIVER_ID = new DriverId("driver-1");
    private static final VehicleId VEHICLE_ID = new VehicleId("vehicle-1");
    private static final RescueReportId RESCUE_REPORT_ID = new RescueReportId("report-1");

    @BeforeEach
    void setUp() {
        service = new EmergencyRescueServiceImpl(emergencyRescueService);
    }

    // ========== confirmSOSReport ==========

    @Test
    void confirmSOSReportShouldReturnOkOnSuccess() {
        when(emergencyRescueService.verifyAndConsumeToken("token-1", "CONFIRM_SOS", new VehicleId("report-1")))
                .thenReturn(Result.ok(null));

        Result<Void, AppError> result = service.confirmSOSReport("report-1", "token-1");

        assertTrue(result.isOk());
        assertNull(result.unwrap());
        verify(emergencyRescueService).verifyAndConsumeToken("token-1", "CONFIRM_SOS", new VehicleId("report-1"));
    }

    @Test
    void confirmSOSReportShouldReturnErrOnFailure() {
        AppError error = AppError.accessDenied("invalid token");
        when(emergencyRescueService.verifyAndConsumeToken("bad-token", "CONFIRM_SOS", new VehicleId("report-1")))
                .thenReturn(Result.err(error));

        Result<Void, AppError> result = service.confirmSOSReport("report-1", "bad-token");

        assertTrue(result.isErr());
        assertEquals("AccessDenied", result.unwrapErr().code());
    }

    // ========== issueRescueToken ==========

    @Test
    void issueRescueTokenShouldReturnTokenOnSuccess() {
        Instant expiresAt = Instant.now().plusSeconds(3600);
        RescueAuthorizationToken token = RescueAuthorizationToken.issue(
                "token-id-1", "issuer", VEHICLE_ID, Set.of("UNLOCK"), Instant.now(), expiresAt);
        when(emergencyRescueService.issueRescueAuthorization(eq(RESCUE_REPORT_ID), any(), eq(3600)))
                .thenReturn(Result.ok(token));

        Result<IEmergencyRescueService.IssueRescueTokenResponse, AppError> result =
                service.issueRescueToken("report-1", List.of("UNLOCK"), 3600);

        assertTrue(result.isOk());
        IEmergencyRescueService.IssueRescueTokenResponse resp = result.unwrap();
        assertEquals("token-id-1", resp.token());
        assertEquals(expiresAt.toEpochMilli(), resp.expiresAt());
        verify(emergencyRescueService).issueRescueAuthorization(
                new RescueReportId("report-1"), List.of("UNLOCK"), 3600);
    }

    @Test
    void issueRescueTokenShouldReturnErrOnFailure() {
        AppError error = AppError.notFound("RescueReport", "report-1");
        when(emergencyRescueService.issueRescueAuthorization(any(), any(), anyInt()))
                .thenReturn(Result.err(error));

        Result<IEmergencyRescueService.IssueRescueTokenResponse, AppError> result =
                service.issueRescueToken("report-1", List.of("UNLOCK"), 3600);

        assertTrue(result.isErr());
        assertEquals("NotFound", result.unwrapErr().code());
    }

    @Test
    void issueRescueTokenShouldHandleEmptyOperations() {
        AppError error = AppError.validationFailed("no operations");
        when(emergencyRescueService.issueRescueAuthorization(any(), any(), anyInt()))
                .thenReturn(Result.err(error));

        Result<IEmergencyRescueService.IssueRescueTokenResponse, AppError> result =
                service.issueRescueToken("report-1", List.of(), 60);

        assertTrue(result.isErr());
        assertEquals("ValidationFailed", result.unwrapErr().code());
    }

    // ========== verifyRescueToken ==========

    @Test
    void verifyRescueTokenShouldReturnValidOnSuccess() {
        when(emergencyRescueService.verifyAndConsumeToken("token-1", "UNLOCK", VEHICLE_ID))
                .thenReturn(Result.ok(null));

        Result<IEmergencyRescueService.VerifyTokenResponse, AppError> result =
                service.verifyRescueToken("token-1", "UNLOCK", VEHICLE_ID);

        assertTrue(result.isOk());
        IEmergencyRescueService.VerifyTokenResponse resp = result.unwrap();
        assertTrue(resp.valid());
        assertEquals("UNLOCK", resp.operation());
    }

    @Test
    void verifyRescueTokenShouldReturnErrOnFailure() {
        AppError error = AppError.accessDenied("expired");
        when(emergencyRescueService.verifyAndConsumeToken("expired-token", "UNLOCK", VEHICLE_ID))
                .thenReturn(Result.err(error));

        Result<IEmergencyRescueService.VerifyTokenResponse, AppError> result =
                service.verifyRescueToken("expired-token", "UNLOCK", VEHICLE_ID);

        assertTrue(result.isErr());
        assertEquals("AccessDenied", result.unwrapErr().code());
    }

    // ========== queryRescueHistory ==========

    @Test
    void queryRescueHistoryShouldReturnPagedResults() {
        List<EmergencyRescueService.RescueRecord> records = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            records.add(new EmergencyRescueService.RescueRecord(
                    "record-" + i, DRIVER_ID.id(), 1000L + i, "COMPLETED"));
        }
        when(emergencyRescueService.queryRescueHistory(DRIVER_ID)).thenReturn(Result.ok(records));

        Result<IEmergencyRescueService.RescueHistoryResponse, AppError> result =
                service.queryRescueHistory(DRIVER_ID, 1, 2);

        assertTrue(result.isOk());
        IEmergencyRescueService.RescueHistoryResponse resp = result.unwrap();
        assertEquals(2, resp.records().size());
        assertEquals(5L, resp.totalCount());
        assertEquals("record-0", resp.records().get(0).recordId());
        verify(emergencyRescueService).queryRescueHistory(DRIVER_ID);
    }

    @Test
    void queryRescueHistoryShouldReturnSecondPage() {
        List<EmergencyRescueService.RescueRecord> records = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            records.add(new EmergencyRescueService.RescueRecord(
                    "record-" + i, DRIVER_ID.id(), 1000L + i, "COMPLETED"));
        }
        when(emergencyRescueService.queryRescueHistory(DRIVER_ID)).thenReturn(Result.ok(records));

        Result<IEmergencyRescueService.RescueHistoryResponse, AppError> result =
                service.queryRescueHistory(DRIVER_ID, 3, 2);

        assertTrue(result.isOk());
        IEmergencyRescueService.RescueHistoryResponse resp = result.unwrap();
        assertEquals(1, resp.records().size());
        assertEquals("record-4", resp.records().get(0).recordId());
    }

    @Test
    void queryRescueHistoryShouldReturnEmptyListWhenPageBeyondRange() {
        List<EmergencyRescueService.RescueRecord> records = List.of(
                new EmergencyRescueService.RescueRecord("record-0", DRIVER_ID.id(), 1000L, "COMPLETED"));
        when(emergencyRescueService.queryRescueHistory(DRIVER_ID)).thenReturn(Result.ok(records));

        Result<IEmergencyRescueService.RescueHistoryResponse, AppError> result =
                service.queryRescueHistory(DRIVER_ID, 5, 10);

        assertTrue(result.isOk());
        assertTrue(result.unwrap().records().isEmpty());
    }

    @Test
    void queryRescueHistoryShouldReturnErrOnServiceFailure() {
        AppError error = AppError.notFound("Driver", "unknown");
        when(emergencyRescueService.queryRescueHistory(any(DriverId.class)))
                .thenReturn(Result.err(error));

        Result<IEmergencyRescueService.RescueHistoryResponse, AppError> result =
                service.queryRescueHistory(new DriverId("unknown"), 1, 10);

        assertTrue(result.isErr());
        assertEquals("NotFound", result.unwrapErr().code());
    }

    @Test
    void queryRescueHistoryShouldHandleExactPageBoundary() {
        List<EmergencyRescueService.RescueRecord> records = List.of(
                new EmergencyRescueService.RescueRecord("record-0", DRIVER_ID.id(), 1000L, "COMPLETED"),
                new EmergencyRescueService.RescueRecord("record-1", DRIVER_ID.id(), 2000L, "COMPLETED"));
        when(emergencyRescueService.queryRescueHistory(DRIVER_ID)).thenReturn(Result.ok(records));

        Result<IEmergencyRescueService.RescueHistoryResponse, AppError> result =
                service.queryRescueHistory(DRIVER_ID, 1, 2);

        assertTrue(result.isOk());
        assertEquals(2, result.unwrap().records().size());
    }

    // ========== createRescueReport ==========

    @Test
    void createRescueReportShouldReturnReportOnSuccess() {
        EmergencyRescueService.RescueReport report = new EmergencyRescueService.RescueReport(
                "report-1", VEHICLE_ID.id(), DRIVER_ID.id(), "loc", "ACTIVE");
        when(emergencyRescueService.createRescueReport(VEHICLE_ID, DRIVER_ID))
                .thenReturn(Result.ok(report));

        Result<IEmergencyRescueService.CreateRescueReportResponse, AppError> result =
                service.createRescueReport(DRIVER_ID, VEHICLE_ID);

        assertTrue(result.isOk());
        IEmergencyRescueService.CreateRescueReportResponse resp = result.unwrap();
        assertEquals("report-1", resp.reportId());
        assertEquals("ACTIVE", resp.status());
    }

    @Test
    void createRescueReportShouldReturnErrOnFailure() {
        AppError error = AppError.invalidState("already active");
        when(emergencyRescueService.createRescueReport(VEHICLE_ID, DRIVER_ID))
                .thenReturn(Result.err(error));

        Result<IEmergencyRescueService.CreateRescueReportResponse, AppError> result =
                service.createRescueReport(DRIVER_ID, VEHICLE_ID);

        assertTrue(result.isErr());
        assertEquals("InvalidState", result.unwrapErr().code());
    }
}
