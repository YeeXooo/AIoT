package com.aiot.interfaces.rest;

import com.aiot.application.AlertApplicationService;
import com.aiot.application.TripApplicationService;
import com.aiot.domain.event.AlertType;
import com.aiot.domain.event.RiskLevel;
import com.aiot.domain.model.SafetyAlertEvent;
import com.aiot.domain.model.Trip;
import com.aiot.domain.model.Vehicle;
import com.aiot.domain.shared.AlertId;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.TripId;
import com.aiot.domain.shared.VehicleId;

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
class SafetyControllerTest {

    @Mock
    private TripApplicationService tripService;

    @Mock
    private AlertApplicationService alertService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SafetyController(tripService, alertService)).build();
    }

    private static Trip sampleTrip(String tripId, String driverId, String vehicleId) {
        return Trip.reconstitute(
                new TripId(tripId),
                new DriverId(driverId),
                new VehicleId(vehicleId),
                LocalDateTime.of(2026, 7, 1, 8, 0),
                null,
                0, 0, null, 1,
                LocalDateTime.of(2026, 7, 1, 8, 0),
                LocalDateTime.of(2026, 7, 1, 8, 0));
    }

    private static Vehicle sampleVehicle(String vehicleId, String plate, String fleetId) {
        return Vehicle.reconstitute(
                new VehicleId(vehicleId),
                plate, "VIN" + vehicleId, "TS-" + vehicleId,
                fleetId, "2.0.0", 1,
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    private static SafetyAlertEvent sampleAlert(String alertId, String driverId, AlertType type, RiskLevel level) {
        return SafetyAlertEvent.reconstitute(
                new AlertId(alertId),
                new TripId("t-" + alertId),
                new DriverId(driverId),
                new VehicleId("v-" + alertId),
                type, level,
                LocalDateTime.of(2026, 7, 1, 9, 30),
                "Sample alert message",
                false, null);
    }

    @Test
    void listTripsShouldReturnTripsForDriver() throws Exception {
        Trip t = sampleTrip("trip-1", "d-001", "v-001");
        when(tripService.listTrips("d-001", null)).thenReturn(List.of(t));

        mockMvc.perform(get("/api/v1/safety/trip/list").param("driverId", "d-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        verify(tripService).listTrips("d-001", null);
    }

    @Test
    void listTripsShouldReturnActiveTrips() throws Exception {
        Trip t = sampleTrip("trip-2", "d-002", "v-002");
        when(tripService.listTrips(isNull(), eq(true))).thenReturn(List.of(t));

        mockMvc.perform(get("/api/v1/safety/trip/list").param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        verify(tripService).listTrips(isNull(), eq(true));
    }

    @Test
    void listTripsShouldReturnTripsForDriverAndActive() throws Exception {
        Trip t = sampleTrip("trip-3", "d-003", "v-003");
        when(tripService.listTrips("d-003", true)).thenReturn(List.of(t));

        mockMvc.perform(get("/api/v1/safety/trip/list")
                        .param("driverId", "d-003")
                        .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        verify(tripService).listTrips("d-003", true);
    }

    @Test
    void listTripsShouldReturnAllTripsWhenNoParams() throws Exception {
        when(tripService.listTrips(isNull(), isNull())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/safety/trip/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(tripService).listTrips(isNull(), isNull());
    }

    @Test
    void listAlertsShouldReturnFilteredAlerts() throws Exception {
        SafetyAlertEvent alert = sampleAlert("a-1", "d-001", AlertType.FATIGUE, RiskLevel.L2_WARNING);
        when(alertService.listAlerts("d-001", "L2_WARNING", "FATIGUE"))
                .thenReturn(List.of(alert));

        mockMvc.perform(get("/api/v1/safety/alert/list")
                        .param("driverId", "d-001")
                        .param("riskLevel", "L2_WARNING")
                        .param("alertType", "FATIGUE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        verify(alertService).listAlerts("d-001", "L2_WARNING", "FATIGUE");
    }

    @Test
    void listAlertsShouldReturnAllAlertsWhenNoParams() throws Exception {
        SafetyAlertEvent a1 = sampleAlert("a-1", "d-001", AlertType.DISTRACTION, RiskLevel.L1_HINT);
        SafetyAlertEvent a2 = sampleAlert("a-2", "d-002", AlertType.ROAD_RAGE, RiskLevel.L3_CRITICAL);
        when(alertService.listAlerts(isNull(), isNull(), isNull()))
                .thenReturn(List.of(a1, a2));

        mockMvc.perform(get("/api/v1/safety/alert/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void listAlertsShouldReturnEmptyListWhenNoAlerts() throws Exception {
        when(alertService.listAlerts(isNull(), isNull(), isNull())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/safety/alert/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listVehiclesShouldReturnVehiclesByFleetId() throws Exception {
        Vehicle v = sampleVehicle("v-001", "京A12345", "fleet-1");
        when(tripService.listVehicles("fleet-1", null)).thenReturn(List.of(v));

        mockMvc.perform(get("/api/v1/safety/vehicle/list").param("fleetId", "fleet-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        verify(tripService).listVehicles("fleet-1", null);
    }

    @Test
    void listVehiclesShouldReturnVehiclesByKeyword() throws Exception {
        Vehicle v = sampleVehicle("v-002", "京B67890", "fleet-2");
        when(tripService.listVehicles(isNull(), eq("京B"))).thenReturn(List.of(v));

        mockMvc.perform(get("/api/v1/safety/vehicle/list").param("keyword", "京B"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        verify(tripService).listVehicles(isNull(), eq("京B"));
    }

    @Test
    void listVehiclesShouldReturnAllVehiclesWhenNoParams() throws Exception {
        when(tripService.listVehicles(isNull(), isNull())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/safety/vehicle/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(tripService).listVehicles(isNull(), isNull());
    }

    @Test
    void listVehiclesShouldReturnVehiclesWithFleetAndKeyword() throws Exception {
        Vehicle v = sampleVehicle("v-003", "京C11111", "fleet-3");
        when(tripService.listVehicles(eq("fleet-3"), eq("京C"))).thenReturn(List.of(v));

        mockMvc.perform(get("/api/v1/safety/vehicle/list")
                        .param("fleetId", "fleet-3")
                        .param("keyword", "京C"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }
}
