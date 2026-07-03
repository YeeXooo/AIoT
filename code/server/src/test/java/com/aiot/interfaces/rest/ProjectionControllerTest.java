package com.aiot.interfaces.rest;

import com.aiot.application.ProjectionApplicationService;
import com.aiot.infra.persistence.AlertProjectionEntity;
import com.aiot.infra.persistence.FleetDashboardProjectionEntity;
import com.aiot.infra.persistence.TrajectoryProjectionEntity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ProjectionControllerTest {

    @Mock
    private ProjectionApplicationService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProjectionController(service)).build();
    }

    private static AlertProjectionEntity sampleAlert(
            String alertId, String driverId, String fleetId, String riskLevel, String alertType) {
        AlertProjectionEntity e = new AlertProjectionEntity();
        e.setAlertId(alertId);
        e.setDriverId(driverId);
        e.setVehicleId("v-" + alertId);
        e.setFleetId(fleetId);
        e.setAlertType(alertType);
        e.setRiskLevel(riskLevel);
        e.setOccurredAt(LocalDateTime.of(2026, 7, 1, 9, 0));
        e.setAlertMsg("Alert message for " + alertId);
        return e;
    }

    private static FleetDashboardProjectionEntity sampleDashboard(String fleetId, String riskLevel, String alertType) {
        FleetDashboardProjectionEntity e = new FleetDashboardProjectionEntity();
        e.setFleetId(fleetId);
        e.setRiskLevel(riskLevel);
        e.setAlertType(alertType);
        e.setAlertCount(5);
        e.setDriverCount(3);
        return e;
    }

    private static TrajectoryProjectionEntity sampleTrajectory(
            String trajId, String tripId, double lat, double lon, double speed) {
        TrajectoryProjectionEntity e = new TrajectoryProjectionEntity();
        e.setTrajectoryId(trajId);
        e.setTripId(tripId);
        e.setVehicleId("v-001");
        e.setDriverId("d-001");
        e.setGpsLatitude(lat);
        e.setGpsLongitude(lon);
        e.setSpeed(speed);
        e.setRecordedAt(LocalDateTime.of(2026, 7, 1, 10, 0));
        return e;
    }

    @Test
    void alertsShouldReturnByFleetId() throws Exception {
        AlertProjectionEntity a = sampleAlert("a-1", "d-001", "fleet-1", "L2_WARNING", "FATIGUE");
        when(service.getAlerts(eq("fleet-1"), isNull())).thenReturn(List.of(a));

        mockMvc.perform(get("/api/v1/projection/alert").param("fleetId", "fleet-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].alertId").value("a-1"))
                .andExpect(jsonPath("$[0].fleetId").value("fleet-1"))
                .andExpect(jsonPath("$[0].riskLevel").value("L2_WARNING"));

        verify(service).getAlerts(eq("fleet-1"), isNull());
    }

    @Test
    void alertsShouldReturnByRiskLevel() throws Exception {
        AlertProjectionEntity a = sampleAlert("a-2", "d-002", "fleet-2", "L3_CRITICAL", "COLLISION_DISABILITY");
        when(service.getAlerts(isNull(), eq("L3_CRITICAL"))).thenReturn(List.of(a));

        mockMvc.perform(get("/api/v1/projection/alert").param("riskLevel", "L3_CRITICAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].alertId").value("a-2"))
                .andExpect(jsonPath("$[0].riskLevel").value("L3_CRITICAL"));

        verify(service).getAlerts(isNull(), eq("L3_CRITICAL"));
    }

    @Test
    void alertsShouldReturnByFleetIdAndRiskLevel() throws Exception {
        AlertProjectionEntity a = sampleAlert("a-3", "d-003", "fleet-3", "L1_HINT", "PERFORMANCE_WARNING");
        when(service.getAlerts(eq("fleet-3"), eq("L1_HINT"))).thenReturn(List.of(a));

        mockMvc.perform(get("/api/v1/projection/alert")
                        .param("fleetId", "fleet-3")
                        .param("riskLevel", "L1_HINT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].alertId").value("a-3"));

        verify(service).getAlerts(eq("fleet-3"), eq("L1_HINT"));
    }

    @Test
    void alertsShouldReturnAllWhenNoParams() throws Exception {
        AlertProjectionEntity a1 = sampleAlert("a-1", "d-001", null, "L2_WARNING", "FATIGUE");
        AlertProjectionEntity a2 = sampleAlert("a-2", "d-002", null, "L1_HINT", "DISTRACTION");
        when(service.getAlerts(isNull(), isNull())).thenReturn(List.of(a1, a2));

        mockMvc.perform(get("/api/v1/projection/alert"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].alertId").value("a-1"))
                .andExpect(jsonPath("$[1].alertId").value("a-2"));

        verify(service).getAlerts(isNull(), isNull());
    }

    @Test
    void alertsShouldReturnNullWhenActiveOnlyTrue() throws Exception {
        mockMvc.perform(get("/api/v1/projection/alert").param("activeOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        verify(service, never()).getAlerts(any(), any());
    }

    @Test
    void alertsShouldDelegateWhenActiveOnlyFalse() throws Exception {
        when(service.getAlerts(isNull(), isNull())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/projection/alert").param("activeOnly", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(service).getAlerts(isNull(), isNull());
    }

    @Test
    void dashboardShouldReturnByFleetId() throws Exception {
        FleetDashboardProjectionEntity d = sampleDashboard("fleet-1", "L2_WARNING", "FATIGUE");
        when(service.getDashboard(eq("fleet-1"))).thenReturn(List.of(d));

        mockMvc.perform(get("/api/v1/projection/dashboard").param("fleetId", "fleet-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fleetId").value("fleet-1"))
                .andExpect(jsonPath("$[0].riskLevel").value("L2_WARNING"))
                .andExpect(jsonPath("$[0].alertType").value("FATIGUE"))
                .andExpect(jsonPath("$[0].alertCount").value(5))
                .andExpect(jsonPath("$[0].driverCount").value(3));

        verify(service).getDashboard(eq("fleet-1"));
    }

    @Test
    void dashboardShouldReturnAllWhenNoFleetId() throws Exception {
        FleetDashboardProjectionEntity d1 = sampleDashboard("fleet-1", "L3_CRITICAL", "COLLISION_DISABILITY");
        FleetDashboardProjectionEntity d2 = sampleDashboard("fleet-2", "L2_WARNING", "ROAD_RAGE");
        d2.setAlertCount(12);
        d2.setDriverCount(7);
        when(service.getDashboard(isNull())).thenReturn(List.of(d1, d2));

        mockMvc.perform(get("/api/v1/projection/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].alertCount").value(5))
                .andExpect(jsonPath("$[1].alertCount").value(12))
                .andExpect(jsonPath("$[1].driverCount").value(7));

        verify(service).getDashboard(isNull());
    }

    @Test
    void dashboardShouldReturnEmptyListWhenNoData() throws Exception {
        when(service.getDashboard(isNull())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/projection/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void trajectoryShouldReturnByTripId() throws Exception {
        TrajectoryProjectionEntity t1 = sampleTrajectory("traj-1", "trip-001", 39.9042, 116.4074, 60.5);
        TrajectoryProjectionEntity t2 = sampleTrajectory("traj-2", "trip-001", 39.9142, 116.4174, 55.0);
        when(service.getTrajectory(eq("trip-001"))).thenReturn(List.of(t1, t2));

        mockMvc.perform(get("/api/v1/projection/trajectory").param("tripId", "trip-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].trajectoryId").value("traj-1"))
                .andExpect(jsonPath("$[0].tripId").value("trip-001"))
                .andExpect(jsonPath("$[0].gpsLatitude").value(39.9042))
                .andExpect(jsonPath("$[0].gpsLongitude").value(116.4074))
                .andExpect(jsonPath("$[0].speed").value(60.5))
                .andExpect(jsonPath("$[1].trajectoryId").value("traj-2"))
                .andExpect(jsonPath("$[1].speed").value(55.0));

        verify(service).getTrajectory(eq("trip-001"));
    }

    @Test
    void trajectoryShouldReturn400WhenTripIdMissing() throws Exception {
        mockMvc.perform(get("/api/v1/projection/trajectory"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trajectoryShouldReturnEmptyListWhenNoPoints() throws Exception {
        when(service.getTrajectory(eq("trip-empty"))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/projection/trajectory").param("tripId", "trip-empty"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void alertsShouldHandleEmptyAlertList() throws Exception {
        when(service.getAlerts(isNull(), isNull())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/projection/alert"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
