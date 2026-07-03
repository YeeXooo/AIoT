package com.aiot.interfaces.rest;

import com.aiot.application.HealthApplicationService;
import com.aiot.infra.persistence.DriverHealthProfileEntity;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class HealthControllerTest {

    @Mock
    private HealthApplicationService healthService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new HealthController(healthService)).build();
    }

    @Test
    void getReturnsProfileWhenFound() throws Exception {
        DriverHealthProfileEntity profile = new DriverHealthProfileEntity();
        profile.setDriverId("d-001");
        profile.setBloodType("A");
        profile.setAllergyHistory("none");
        when(healthService.get("d-001")).thenReturn(Optional.of(profile));

        mockMvc.perform(get("/api/v1/health/{driverId}", "d-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverId").value("d-001"))
                .andExpect(jsonPath("$.bloodType").value("A"))
                .andExpect(jsonPath("$.allergyHistory").value("none"));
    }

    @Test
    void getReturnsProfileWithAllFieldsPopulated() throws Exception {
        DriverHealthProfileEntity profile = new DriverHealthProfileEntity();
        profile.setDriverId("d-full");
        profile.setBloodType("AB");
        profile.setAllergyHistory("pollen");
        profile.setChronicHistory("hypertension");
        profile.setMedicationHistory("aspirin");
        profile.setBaselineVitals("120/80");
        profile.setEmergencyContact("John Doe, 13900139000");
        when(healthService.get("d-full")).thenReturn(Optional.of(profile));

        mockMvc.perform(get("/api/v1/health/{driverId}", "d-full"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverId").value("d-full"))
                .andExpect(jsonPath("$.bloodType").value("AB"))
                .andExpect(jsonPath("$.allergyHistory").value("pollen"))
                .andExpect(jsonPath("$.chronicHistory").value("hypertension"))
                .andExpect(jsonPath("$.medicationHistory").value("aspirin"))
                .andExpect(jsonPath("$.baselineVitals").value("120/80"))
                .andExpect(jsonPath("$.emergencyContact").value("John Doe, 13900139000"));
    }

    @Test
    void getReturnsNullWhenNotFound() throws Exception {
        when(healthService.get("unknown")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/health/{driverId}", "unknown"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    void getWhenServiceThrowsException() {
        when(healthService.get("d-001")).thenThrow(new RuntimeException("DB error"));

        ServletException ex = assertThrows(ServletException.class, () -> {
            mockMvc.perform(get("/api/v1/health/{driverId}", "d-001"));
        });
        assertTrue(ex.getCause() instanceof RuntimeException);
        assertEquals("DB error", ex.getCause().getMessage());
    }

    @Test
    void updateSavesProfileAndReturnsIt() throws Exception {
        when(healthService.save(any(DriverHealthProfileEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/v1/health/{driverId}", "d-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bloodType\":\"B\",\"allergyHistory\":\"penicillin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverId").value("d-001"))
                .andExpect(jsonPath("$.bloodType").value("B"))
                .andExpect(jsonPath("$.allergyHistory").value("penicillin"));

        ArgumentCaptor<DriverHealthProfileEntity> captor = ArgumentCaptor.forClass(DriverHealthProfileEntity.class);
        verify(healthService).save(captor.capture());
        assertEquals("d-001", captor.getValue().getDriverId());
        assertEquals("B", captor.getValue().getBloodType());
        assertEquals("penicillin", captor.getValue().getAllergyHistory());
    }

    @Test
    void updateOverwritesDriverIdFromPathVariable() throws Exception {
        when(healthService.save(any(DriverHealthProfileEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/v1/health/{driverId}", "d-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driverId\":\"ignored-from-body\",\"bloodType\":\"O\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverId").value("d-002"))
                .andExpect(jsonPath("$.bloodType").value("O"));

        ArgumentCaptor<DriverHealthProfileEntity> captor = ArgumentCaptor.forClass(DriverHealthProfileEntity.class);
        verify(healthService).save(captor.capture());
        assertEquals("d-002", captor.getValue().getDriverId());
    }

    @Test
    void updateWithEmptyBody() throws Exception {
        when(healthService.save(any(DriverHealthProfileEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/v1/health/{driverId}", "d-003")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverId").value("d-003"));
    }

    @Test
    void updateWhenServiceThrowsException() {
        when(healthService.save(any(DriverHealthProfileEntity.class))).thenThrow(new RuntimeException("DB error"));

        ServletException ex = assertThrows(ServletException.class, () -> {
            mockMvc.perform(put("/api/v1/health/{driverId}", "d-001")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"bloodType\":\"B\"}"));
        });
        assertTrue(ex.getCause() instanceof RuntimeException);
        assertEquals("DB error", ex.getCause().getMessage());
    }
}
