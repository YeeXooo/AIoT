package com.aiot.interfaces.rest;

import com.aiot.application.fleet.IFleetManagementService;
import com.aiot.domain.model.TimeRange;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class FleetControllerTest {

    @Mock
    private IFleetManagementService fleetService;

    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FleetController(fleetService)).build();
    }

    @Test
    void getFatigueDistributionReturnsOk() throws Exception {
        when(fleetService.getFatigueDistribution("fleet-east-1"))
                .thenReturn(Result.ok(new IFleetManagementService.GetFatigueDistributionResponse(
                        Map.of("L0", 0.5, "L1", 0.3), "2026-07-10T10:00:00")));

        mockMvc.perform(get("/api/v1/fleet/{fleetId}/fatigue-distribution", "f1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distribution.L0").value(0.5))
                .andExpect(jsonPath("$.dataFreshness").value("2026-07-10T10:00:00"))
                .andExpect(jsonPath("$.heatmapData").isArray());
    }

    @Test
    void getFatigueDistributionReturns404WhenNotFound() throws Exception {
        when(fleetService.getFatigueDistribution("fleet-east-1"))
                .thenReturn(Result.err(AppError.notFound("Fleet", "fleet-east-1")));

        mockMvc.perform(get("/api/v1/fleet/{fleetId}/fatigue-distribution", "f1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOfflineVehiclesReturnsList() throws Exception {
        when(fleetService.getOfflineVehicles("fleet-west-1"))
                .thenReturn(Result.ok(new IFleetManagementService.GetOfflineVehiclesResponse(
                        List.of(new IFleetManagementService.OfflineVehicleSummary(
                                "v001", "京A12345", "d001", "sensor_failure", 1700000000L)))));

        mockMvc.perform(get("/api/v1/fleet/{fleetId}/offline-vehicles", "f2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offlineVehicles").isArray())
                .andExpect(jsonPath("$.offlineVehicles[0].vehicleId").value("v001"));
    }

    @Test
    void queryTrajectoryWithDefaultVehicle() throws Exception {
        when(fleetService.queryVehicleTrajectory(any(), any(), eq(0), eq(100)))
                .thenReturn(Result.ok(new IFleetManagementService.TrajectoryResponse(
                        List.of(), 0)));

        mockMvc.perform(get("/api/v1/fleet/{fleetId}/trajectory", "f1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trajectoryPoints").isArray());
    }

    @Test
    void drillDownHighRiskReturnsDrivers() throws Exception {
        when(fleetService.drillDownHighRisk("fleet-east-1", "L3_CRITICAL", 0, 10))
                .thenReturn(Result.ok(new IFleetManagementService.DrillDownResponse(
                        List.of(new IFleetManagementService.HighRiskDriverSummary(
                                "d001", "DriverA", 85.0, "summary", List.of("疲劳"))), 1)));

        mockMvc.perform(get("/api/v1/fleet/{fleetId}/high-risk-drivers", "f1")
                        .param("riskLevel", "L3_CRITICAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.drivers[0].driverId").value("d001"))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    void generateReportReturnsReport() throws Exception {
        when(fleetService.generateReport(any(), any(), eq("PERFORMANCE")))
                .thenReturn(Result.ok(new IFleetManagementService.GenerateReportResponse(
                        "rpt-1", new Object(), "/download/rpt-1", false)));

        String body = mapper.writeValueAsString(Map.of(
                "driverId", "d001",
                "timeRange", Map.of("start", "2025-01-01T00:00:00", "end", "2025-01-02T00:00:00"),
                "reportType", "PERFORMANCE"));

        mockMvc.perform(post("/api/v1/fleet/reports")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").value("rpt-1"))
                .andExpect(jsonPath("$.downloadUrl").value("/download/rpt-1"));
    }

    @Test
    void downloadReportReturnsJsonByDefault() throws Exception {
        String disposition = mockMvc.perform(get("/api/v1/fleet/reports/{reportId}/download", "rpt-x")
                        .param("format", "json"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andReturn().getResponse().getHeader("Content-Disposition");
        org.junit.jupiter.api.Assertions.assertNotNull(disposition);
        org.junit.jupiter.api.Assertions.assertTrue(disposition.contains("rpt-x.json"));
    }

    @Test
    void downloadReportReturnsPdfWhenRequested() throws Exception {
        mockMvc.perform(get("/api/v1/fleet/reports/{reportId}/download", "rpt-x")
                        .param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));
    }

    @Test
    void subscribePerformanceWarningReturnsSubscription() throws Exception {
        when(fleetService.subscribePerformanceWarning(any(), eq("fleet-east-1")))
                .thenReturn(Result.ok(new IFleetManagementService.SubscribeResponse("sub-1")));

        String body = mapper.writeValueAsString(Map.of("adminId", "admin-1", "fleetId", "fleet-east-1"));

        mockMvc.perform(post("/api/v1/fleet/performance-warning-subscription")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptionId").value("sub-1"));
    }

    @Test
    void unsubscribeReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/fleet/performance-warning-subscription/{subscriptionId}", "sub-1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void fleetIdMappingf1MapsToFleetEast1() throws Exception {
        when(fleetService.getFatigueDistribution("fleet-east-1"))
                .thenReturn(Result.ok(new IFleetManagementService.GetFatigueDistributionResponse(
                        Map.of(), "")));

        mockMvc.perform(get("/api/v1/fleet/{fleetId}/fatigue-distribution", "f1"))
                .andExpect(status().isOk());
        verify(fleetService).getFatigueDistribution("fleet-east-1");
    }

    @Test
    void fleetIdMappingf2MapsToFleetWest1() throws Exception {
        when(fleetService.getFatigueDistribution("fleet-west-1"))
                .thenReturn(Result.ok(new IFleetManagementService.GetFatigueDistributionResponse(
                        Map.of(), "")));

        mockMvc.perform(get("/api/v1/fleet/{fleetId}/fatigue-distribution", "f2"))
                .andExpect(status().isOk());
        verify(fleetService).getFatigueDistribution("fleet-west-1");
    }

    @Test
    void unknownFleetIdPassedThrough() throws Exception {
        when(fleetService.getFatigueDistribution("unknown-fleet"))
                .thenReturn(Result.ok(new IFleetManagementService.GetFatigueDistributionResponse(Map.of(), "")));

        mockMvc.perform(get("/api/v1/fleet/{fleetId}/fatigue-distribution", "unknown-fleet"))
                .andExpect(status().isOk());
        verify(fleetService).getFatigueDistribution("unknown-fleet");
    }
}
