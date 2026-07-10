package com.aiot.interfaces.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class VehiclesControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new VehiclesController()).build();
    }

    @Test
    void queryWindowStatusReturnsFourWindows() throws Exception {
        mockMvc.perform(get("/api/v1/vehicles/{vehicleId}/windows", "v001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.windowStatuses").isArray())
                .andExpect(jsonPath("$.windowStatuses.length()").value(4));
    }

    @Test
    void queryWindowStatusContainsExpectedPositions() throws Exception {
        mockMvc.perform(get("/api/v1/vehicles/{vehicleId}/windows", "v001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.windowStatuses[0].windowPosition").value("FRONT_LEFT"))
                .andExpect(jsonPath("$.windowStatuses[1].windowPosition").value("FRONT_RIGHT"))
                .andExpect(jsonPath("$.windowStatuses[2].windowPosition").value("REAR_LEFT"))
                .andExpect(jsonPath("$.windowStatuses[3].windowPosition").value("REAR_RIGHT"));
    }

    @Test
    void queryWindowStatusReturnsClosedStateForFrontLeft() throws Exception {
        mockMvc.perform(get("/api/v1/vehicles/{vehicleId}/windows", "v001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.windowStatuses[0].state").value("CLOSED"));
    }

    @Test
    void queryWindowStatusReturnsOpenStateForRearLeft() throws Exception {
        mockMvc.perform(get("/api/v1/vehicles/{vehicleId}/windows", "v001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.windowStatuses[2].state").value("OPEN"));
    }

    @Test
    void queryWindowStatusWorksWithDifferentVehicleId() throws Exception {
        mockMvc.perform(get("/api/v1/vehicles/{vehicleId}/windows", "v999-x1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.windowStatuses").isArray())
                .andExpect(jsonPath("$.windowStatuses.length()").value(4));
    }
}
