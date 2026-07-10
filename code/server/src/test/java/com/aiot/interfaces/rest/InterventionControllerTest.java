package com.aiot.interfaces.rest;

import com.aiot.application.intervention.IInterventionService;
import com.aiot.domain.model.*;
import com.aiot.domain.repository.TripRepository;
import com.aiot.domain.shared.*;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class InterventionControllerTest {

    @Mock
    private IInterventionService interventionService;
    @Mock
    private TripRepository tripRepository;

    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new InterventionController(interventionService, tripRepository)).build();
    }

    @Test
    void queryInterventionStatusReturnsActiveInstructions() throws Exception {
        var item = new IInterventionService.InterventionItem(
                "ACTIVE", "FATIGUE", "L2_WARNING", "SLOW_DOWN", 1700000000L);

        when(interventionService.queryInterventionStatus(any()))
                .thenReturn(Result.ok(new IInterventionService.QueryInterventionResponse(
                        "trip-1", List.of(item), 1, 0, 10)));

        mockMvc.perform(get("/api/v1/trips/{tripId}/interventions/active", "trip-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value("trip-1"))
                .andExpect(jsonPath("$.activeInstructions").isArray());
    }

    @Test
    void queryInterventionStatusExcludesOverrideTypes() throws Exception {
        var override = new IInterventionService.InterventionItem(
                "ACTIVE", "FATIGUE", "L3_CRITICAL", "OVERRIDE_TURNING", 1700000000L);

        when(interventionService.queryInterventionStatus(any()))
                .thenReturn(Result.ok(new IInterventionService.QueryInterventionResponse(
                        "trip-1", List.of(override), 1, 0, 10)));

        mockMvc.perform(get("/api/v1/trips/{tripId}/interventions/active", "trip-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeInstructions").isEmpty());
    }

    @Test
    void queryInterventionStatusReturns404OnError() throws Exception {
        when(interventionService.queryInterventionStatus(any()))
                .thenReturn(Result.err(AppError.notFound("Trip", "trip-999")));

        mockMvc.perform(get("/api/v1/trips/{tripId}/interventions/active", "trip-999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reportOverrideSucceeds() throws Exception {
        Trip trip = mock(Trip.class);
        when(trip.driverId()).thenReturn(new DriverId("d001"));
        when(tripRepository.findById("trip-1")).thenReturn(Optional.of(trip));

        when(interventionService.reportOverride(any(), any()))
                .thenReturn(Result.ok(new IInterventionService.ReportOverrideResponse(
                        "ABORTED", System.currentTimeMillis(), true)));

        String body = mapper.writeValueAsString(Map.of("type", "BRAKE", "timestamp", 1700000000000L));

        mockMvc.perform(post("/api/v1/trips/{tripId}/override", "trip-1")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true));
    }

    @Test
    void reportOverrideWithUnknownTypeReturns400() throws Exception {
        String body = mapper.writeValueAsString(Map.of("type", "UNKNOWN_ACTION", "timestamp", 1700000000000L));

        mockMvc.perform(post("/api/v1/trips/{tripId}/override", "trip-1")
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ValidationFailed"));
    }

    @Test
    void reportOverrideWithNonexistentTripReturns404() throws Exception {
        when(tripRepository.findById("trip-999")).thenReturn(Optional.empty());

        String body = mapper.writeValueAsString(Map.of("type", "STEER", "timestamp", 1700000000000L));

        mockMvc.perform(post("/api/v1/trips/{tripId}/override", "trip-999")
                        .contentType("application/json").content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void reportOverrideWithAccelerateType() throws Exception {
        Trip trip = mock(Trip.class);
        when(trip.driverId()).thenReturn(new DriverId("d001"));
        when(tripRepository.findById("trip-1")).thenReturn(Optional.of(trip));

        when(interventionService.reportOverride(any(), any()))
                .thenReturn(Result.ok(new IInterventionService.ReportOverrideResponse(
                        "CONTINUE", System.currentTimeMillis(), false)));

        String body = mapper.writeValueAsString(Map.of("type", "ACCELERATE", "timestamp", 1700000000000L));

        mockMvc.perform(post("/api/v1/trips/{tripId}/override", "trip-1")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(false));
    }

    @Test
    void reportOverrideIsCaseInsensitive() throws Exception {
        Trip trip = mock(Trip.class);
        when(trip.driverId()).thenReturn(new DriverId("d001"));
        when(tripRepository.findById("trip-1")).thenReturn(Optional.of(trip));

        when(interventionService.reportOverride(any(), any()))
                .thenReturn(Result.ok(new IInterventionService.ReportOverrideResponse(
                        "ABORTED", System.currentTimeMillis(), true)));

        String body = mapper.writeValueAsString(Map.of("type", "brake", "timestamp", 1700000000000L));

        mockMvc.perform(post("/api/v1/trips/{tripId}/override", "trip-1")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true));
    }
}
