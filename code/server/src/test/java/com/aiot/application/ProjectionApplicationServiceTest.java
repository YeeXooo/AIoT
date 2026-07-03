package com.aiot.application;

import com.aiot.infra.persistence.AlertProjectionEntity;
import com.aiot.infra.persistence.FleetDashboardProjectionEntity;
import com.aiot.infra.persistence.TrajectoryProjectionEntity;
import com.aiot.infra.repository.AlertProjectionJpaRepository;
import com.aiot.infra.repository.FleetDashboardProjectionJpaRepository;
import com.aiot.infra.repository.TrajectoryProjectionJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectionApplicationServiceTest {

    @Mock
    private AlertProjectionJpaRepository alertRepo;

    @Mock
    private FleetDashboardProjectionJpaRepository fleetRepo;

    @Mock
    private TrajectoryProjectionJpaRepository trajRepo;

    private ProjectionApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ProjectionApplicationService(alertRepo, fleetRepo, trajRepo);
    }

    @Test
    void getAlertsShouldFilterByFleetIdWhenProvided() {
        var alert = new AlertProjectionEntity();
        alert.setAlertId("alert-1");
        alert.setFleetId("fleet-1");
        when(alertRepo.findByFleetId("fleet-1")).thenReturn(List.of(alert));

        var result = service.getAlerts("fleet-1", null);

        assertEquals(1, result.size());
        assertEquals("fleet-1", result.get(0).getFleetId());
        verify(alertRepo, times(1)).findByFleetId("fleet-1");
    }

    @Test
    void getAlertsShouldFilterByFleetIdOverRiskLevel() {
        var alert = new AlertProjectionEntity();
        alert.setAlertId("alert-1");
        alert.setFleetId("fleet-1");
        when(alertRepo.findByFleetId("fleet-1")).thenReturn(List.of(alert));

        var result = service.getAlerts("fleet-1", "L2_WARNING");

        assertEquals(1, result.size());
        verify(alertRepo, times(1)).findByFleetId("fleet-1");
        verify(alertRepo, never()).findByRiskLevel(any());
    }

    @Test
    void getAlertsShouldFilterByRiskLevelWhenNoFleetId() {
        var alert = new AlertProjectionEntity();
        alert.setAlertId("alert-1");
        alert.setRiskLevel("L3_CRITICAL");
        when(alertRepo.findByRiskLevel("L3_CRITICAL")).thenReturn(List.of(alert));

        var result = service.getAlerts(null, "L3_CRITICAL");

        assertEquals(1, result.size());
        assertEquals("L3_CRITICAL", result.get(0).getRiskLevel());
        verify(alertRepo, times(1)).findByRiskLevel("L3_CRITICAL");
    }

    @Test
    void getAlertsShouldReturnAllWhenNoParams() {
        var alert1 = new AlertProjectionEntity();
        alert1.setAlertId("alert-1");
        var alert2 = new AlertProjectionEntity();
        alert2.setAlertId("alert-2");
        when(alertRepo.findAll()).thenReturn(List.of(alert1, alert2));

        var result = service.getAlerts(null, null);

        assertEquals(2, result.size());
        verify(alertRepo, times(1)).findAll();
    }

    @Test
    void getAlertsShouldReturnAllWhenFleetIdEmpty() {
        var alert = new AlertProjectionEntity();
        alert.setAlertId("alert-1");
        when(alertRepo.findAll()).thenReturn(List.of(alert));

        var result = service.getAlerts("", null);

        assertEquals(1, result.size());
        verify(alertRepo, times(1)).findAll();
    }

    @Test
    void getAlertsShouldReturnAllWhenFleetIdEmptyAndRiskLevelProvided() {
        var alert = new AlertProjectionEntity();
        alert.setAlertId("alert-1");
        alert.setRiskLevel("L2_WARNING");
        when(alertRepo.findByRiskLevel("L2_WARNING")).thenReturn(List.of(alert));

        var result = service.getAlerts("", "L2_WARNING");

        assertEquals(1, result.size());
        verify(alertRepo, times(1)).findByRiskLevel("L2_WARNING");
    }

    @Test
    void getAlertsShouldReturnEmptyWhenNoAlerts() {
        when(alertRepo.findAll()).thenReturn(List.of());

        var result = service.getAlerts(null, null);

        assertTrue(result.isEmpty());
    }

    @Test
    void getDashboardShouldFilterByFleetIdWhenProvided() {
        var dashboard = new FleetDashboardProjectionEntity();
        dashboard.setFleetId("fleet-1");
        dashboard.setRiskLevel("L2_WARNING");
        dashboard.setAlertType("FATIGUE");
        when(fleetRepo.findByFleetId("fleet-1")).thenReturn(List.of(dashboard));

        var result = service.getDashboard("fleet-1");

        assertEquals(1, result.size());
        assertEquals("fleet-1", result.get(0).getFleetId());
        verify(fleetRepo, times(1)).findByFleetId("fleet-1");
    }

    @Test
    void getDashboardShouldReturnAllWhenFleetIdIsNull() {
        var dashboard1 = new FleetDashboardProjectionEntity();
        dashboard1.setFleetId("fleet-1");
        dashboard1.setRiskLevel("L2_WARNING");
        dashboard1.setAlertType("FATIGUE");
        var dashboard2 = new FleetDashboardProjectionEntity();
        dashboard2.setFleetId("fleet-2");
        dashboard2.setRiskLevel("L3_CRITICAL");
        dashboard2.setAlertType("DISTRACTION");
        when(fleetRepo.findAll()).thenReturn(List.of(dashboard1, dashboard2));

        var result = service.getDashboard(null);

        assertEquals(2, result.size());
        verify(fleetRepo, times(1)).findAll();
    }

    @Test
    void getDashboardShouldReturnAllWhenFleetIdIsEmpty() {
        var dashboard = new FleetDashboardProjectionEntity();
        dashboard.setFleetId("fleet-1");
        dashboard.setRiskLevel("L2_WARNING");
        dashboard.setAlertType("FATIGUE");
        when(fleetRepo.findAll()).thenReturn(List.of(dashboard));

        var result = service.getDashboard("");

        assertEquals(1, result.size());
        verify(fleetRepo, times(1)).findAll();
    }

    @Test
    void getDashboardShouldReturnEmptyWhenNoDashboard() {
        when(fleetRepo.findAll()).thenReturn(List.of());

        var result = service.getDashboard(null);

        assertTrue(result.isEmpty());
    }

    @Test
    void getTrajectoryShouldReturnTrajectoriesByTripId() {
        var traj = new TrajectoryProjectionEntity();
        traj.setTrajectoryId("traj-1");
        traj.setTripId("trip-1");
        traj.setVehicleId("vehicle-1");
        traj.setDriverId("driver-1");
        when(trajRepo.findByTripIdOrderByRecordedAtAsc("trip-1")).thenReturn(List.of(traj));

        var result = service.getTrajectory("trip-1");

        assertEquals(1, result.size());
        assertEquals("trip-1", result.get(0).getTripId());
        verify(trajRepo, times(1)).findByTripIdOrderByRecordedAtAsc("trip-1");
    }

    @Test
    void getTrajectoryShouldReturnMultipleTrajectories() {
        var traj1 = new TrajectoryProjectionEntity();
        traj1.setTrajectoryId("traj-1");
        traj1.setTripId("trip-1");
        var traj2 = new TrajectoryProjectionEntity();
        traj2.setTrajectoryId("traj-2");
        traj2.setTripId("trip-1");
        when(trajRepo.findByTripIdOrderByRecordedAtAsc("trip-1")).thenReturn(List.of(traj1, traj2));

        var result = service.getTrajectory("trip-1");

        assertEquals(2, result.size());
        verify(trajRepo, times(1)).findByTripIdOrderByRecordedAtAsc("trip-1");
    }

    @Test
    void getTrajectoryShouldReturnEmptyWhenNoTrajectories() {
        when(trajRepo.findByTripIdOrderByRecordedAtAsc("unknown")).thenReturn(List.of());

        var result = service.getTrajectory("unknown");

        assertTrue(result.isEmpty());
    }

    @Test
    void getTrajectoryShouldHandleNullTripId() {
        when(trajRepo.findByTripIdOrderByRecordedAtAsc(null)).thenReturn(List.of());

        var result = service.getTrajectory(null);

        assertTrue(result.isEmpty());
    }
}
