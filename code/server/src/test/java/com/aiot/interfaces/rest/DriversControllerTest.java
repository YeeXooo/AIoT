package com.aiot.interfaces.rest;

import com.aiot.application.risk.IRiskMonitoringService;
import com.aiot.domain.model.Trip;
import com.aiot.domain.repository.TripRepository;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DriversControllerTest {

    @Mock
    private IRiskMonitoringService riskMonitoringService;
    @Mock
    private TripRepository tripRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new DriversController(riskMonitoringService, tripRepository)).build();
    }

    @Test
    void getRiskStatusReturnsOk() throws Exception {
        when(riskMonitoringService.getDriverRiskStatus(any()))
                .thenReturn(Result.ok(new IRiskMonitoringService.GetDriverRiskStatusResponse(
                        "d001", "green", List.of(), LocalDateTime.now())));
        when(tripRepository.findActiveTrips()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/drivers/{driverId}/risk-status", "d001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasActiveTrip").value(false))
                .andExpect(jsonPath("$.derivedStatusColor").value("green"));
    }

    @Test
    void getRiskStatusWithActiveTrip() throws Exception {
        when(riskMonitoringService.getDriverRiskStatus(any()))
                .thenReturn(Result.ok(new IRiskMonitoringService.GetDriverRiskStatusResponse(
                        "d001", "yellow", List.of(), LocalDateTime.now())));
        Trip activeTrip = mock(Trip.class);
        com.aiot.domain.shared.DriverId driverId = new com.aiot.domain.shared.DriverId("d001");
        when(activeTrip.driverId()).thenReturn(driverId);
        when(tripRepository.findActiveTrips()).thenReturn(List.of(activeTrip));

        mockMvc.perform(get("/api/v1/drivers/{driverId}/risk-status", "d001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasActiveTrip").value(true));
    }

    @Test
    void getRiskStatusWithActiveRisks() throws Exception {
        var risk1 = new IRiskMonitoringService.ActiveRiskInfo(
                "FATIGUE", "L2_WARNING", "疲劳驾驶", LocalDateTime.now());
        var risk2 = new IRiskMonitoringService.ActiveRiskInfo(
                "DISTRACTION", "L1_HINT", "分心驾驶", LocalDateTime.now());

        when(riskMonitoringService.getDriverRiskStatus(any()))
                .thenReturn(Result.ok(new IRiskMonitoringService.GetDriverRiskStatusResponse(
                        "d001", "red", List.of(risk1, risk2), LocalDateTime.now())));
        when(tripRepository.findActiveTrips()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/drivers/{driverId}/risk-status", "d001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeAlerts.length()").value(2))
                .andExpect(jsonPath("$.activeAlerts[0].alertType").value("FATIGUE"));
    }

    @Test
    void getRiskStatusReturns404WhenNotFound() throws Exception {
        when(riskMonitoringService.getDriverRiskStatus(any()))
                .thenReturn(Result.err(AppError.notFound("Driver", "d999")));

        mockMvc.perform(get("/api/v1/drivers/{driverId}/risk-status", "d999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void queryAlertHistoryReturnsAlerts() throws Exception {
        var item = new IRiskMonitoringService.AlertHistoryItem(
                "alert-1", "FATIGUE", "L2_WARNING", "trip-1", "d001", "v001",
                LocalDateTime.of(2025, 1, 1, 10, 0), "疲劳驾驶告警");

        when(riskMonitoringService.queryAlertHistory(any(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(10)))
                .thenReturn(Result.ok(new IRiskMonitoringService.QueryAlertHistoryResponse(
                        List.of(item), 1, 0, 10)));

        mockMvc.perform(get("/api/v1/drivers/{driverId}/alerts", "d001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts.length()").value(1))
                .andExpect(jsonPath("$.alerts[0].alertId").value("alert-1"))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    void queryAlertHistoryWithDateFilters() throws Exception {
        when(riskMonitoringService.queryAlertHistory(any(), any(), any(), isNull(), isNull(), eq(0), eq(5)))
                .thenReturn(Result.ok(new IRiskMonitoringService.QueryAlertHistoryResponse(
                        List.of(), 0, 0, 5)));

        mockMvc.perform(get("/api/v1/drivers/{driverId}/alerts", "d001")
                        .param("from", "2025-01-01T00:00:00")
                        .param("to", "2025-01-02T00:00:00")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts").isEmpty());
    }

    @Test
    void queryAlertHistoryReturns404OnError() throws Exception {
        when(riskMonitoringService.queryAlertHistory(any(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(10)))
                .thenReturn(Result.err(AppError.notFound("Driver", "d999")));

        mockMvc.perform(get("/api/v1/drivers/{driverId}/alerts", "d999"))
                .andExpect(status().isNotFound());
    }
}
