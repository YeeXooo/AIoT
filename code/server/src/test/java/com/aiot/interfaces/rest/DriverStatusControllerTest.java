package com.aiot.interfaces.rest;

import com.aiot.application.LatestSensorDataStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DriverStatusControllerTest {

    private MockMvc mockMvc;
    private LatestSensorDataStore store;

    @BeforeEach
    void setUp() {
        store = new LatestSensorDataStore();
        mockMvc = MockMvcBuilders.standaloneSetup(new DriverStatusController(store)).build();
    }

    @Test
    void getStatusWithNoDataReturnsDefaults() throws Exception {
        mockMvc.perform(get("/api/v1/drivers/{driverId}/status", "d001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverId").value("d001"))
                .andExpect(jsonPath("$.tripStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.perception.fatigueLevel").value("NONE"))
                .andExpect(jsonPath("$.physiologicalSummary.heartRate").value(72.0));
    }

    @Test
    void getStatusWithYoloActiveHighRisk() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("yolo_active", true);
        data.put("yolo_confidence", 0.95);
        data.put("yolo_perclos", 0.5);
        data.put("yolo_yawn", 5.0);
        data.put("yolo_hands_off", 0.8);
        data.put("yolo_head_nod", 20.0);
        store.update("yolo_dms", data);

        mockMvc.perform(get("/api/v1/drivers/{driverId}/status", "d001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perception.fatigueLevel").value("L2"))
                .andExpect(jsonPath("$.perception.distractionLevel").value("L2"))
                .andExpect(jsonPath("$.perception.handsOnWheel").value(false))
                .andExpect(jsonPath("$.perception.emotion").value("疲劳"));
    }

    @Test
    void getStatusWithPhysiologicalData() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("hr", 90);
        data.put("spo2", 96);
        data.put("perclos", 0.1);
        store.update("dms", data);

        mockMvc.perform(get("/api/v1/drivers/{driverId}/status", "d001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.physiologicalSummary.heartRate").value(90.0))
                .andExpect(jsonPath("$.physiologicalSummary.spo2").value(96.0));
    }

    @Test
    void getStatusWithGpsLocation() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("lat", 39.9042);
        data.put("lon", 116.4074);
        data.put("perclos", 0.1);
        store.update("dms", data);

        mockMvc.perform(get("/api/v1/drivers/{driverId}/status", "d001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gpsLocation.latitude").value(39.9042))
                .andExpect(jsonPath("$.gpsLocation.longitude").value(116.4074));
    }

    @Test
    void getStatusWithActiveAlerts() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("risk", 3.0);
        data.put("perclos", 0.1);
        store.update("dms", data);

        mockMvc.perform(get("/api/v1/drivers/{driverId}/status", "d001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeAlertLevels.SYSTEM_RISK").value("L3_CRITICAL"));
    }

    @Test
    void getStatusWithNonYoloSensorData() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("perclos", 0.4);
        store.update("dms", data);

        mockMvc.perform(get("/api/v1/drivers/{driverId}/status", "d001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perception.fatigueLevel").value("L2"))
                .andExpect(jsonPath("$.perception.handsOnWheel").value(true));
    }

    @Test
    void getStatusAlwaysIncludesVehicleAndSpeed() throws Exception {
        mockMvc.perform(get("/api/v1/drivers/{driverId}/status", "d123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleId").isString())
                .andExpect(jsonPath("$.speed").value(60));
    }
}
