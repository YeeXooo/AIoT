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
class PerceptionControllerTest {

    private MockMvc mockMvc;
    private LatestSensorDataStore store;

    @BeforeEach
    void setUp() {
        store = new LatestSensorDataStore();
        mockMvc = MockMvcBuilders.standaloneSetup(new PerceptionController(store)).build();
    }

    @Test
    void getLatestWithNoDataReturnsYoloInactive() throws Exception {
        mockMvc.perform(get("/api/v1/drivers/{driverId}/perception/latest", "d001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverId").value("d001"))
                .andExpect(jsonPath("$.yoloActive").value(false));
    }

    @Test
    void getLatestWithYoloActiveHighFatigue() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("yolo_active", true);
        data.put("yolo_confidence", 0.95);
        data.put("yolo_perclos", 0.5);
        data.put("yolo_yawn", 5.0);
        data.put("yolo_head_nod", 20.0);
        data.put("yolo_hands_off", 0.8);
        store.update("yolo_dms", data);

        mockMvc.perform(get("/api/v1/drivers/{driverId}/perception/latest", "d001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yoloActive").value(true))
                .andExpect(jsonPath("$.fatigueLevel").value("L2"))
                .andExpect(jsonPath("$.distractionLevel").value("L2"))
                .andExpect(jsonPath("$.handsOnWheel").value(false))
                .andExpect(jsonPath("$.emotion").value("疲劳"));
    }

    @Test
    void getLatestWithYoloActiveNormal() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("yolo_active", true);
        data.put("yolo_confidence", 0.9);
        data.put("yolo_perclos", 0.1);
        data.put("yolo_yawn", 1.0);
        data.put("yolo_head_nod", 2.0);
        data.put("yolo_hands_off", 0.2);
        store.update("yolo_dms", data);

        mockMvc.perform(get("/api/v1/drivers/{driverId}/perception/latest", "d001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yoloActive").value(true))
                .andExpect(jsonPath("$.fatigueLevel").value("NONE"))
                .andExpect(jsonPath("$.distractionLevel").value("NONE"))
                .andExpect(jsonPath("$.handsOnWheel").value(true))
                .andExpect(jsonPath("$.emotion").value("正常"));
    }

    @Test
    void getLatestWithNonYoloSensorData() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("perclos", 0.4);
        data.put("yawn", 4.0);
        data.put("hands_on", "1");
        data.put("emotion", "焦躁");
        data.put("risk", 2.0);
        data.put("hr", 88);
        data.put("spo2", 97);
        store.update("dms", data);

        mockMvc.perform(get("/api/v1/drivers/{driverId}/perception/latest", "d001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yoloActive").value(false))
                .andExpect(jsonPath("$.fatigueLevel").value("L2"))
                .andExpect(jsonPath("$.distractionLevel").value("L2"))
                .andExpect(jsonPath("$.emotion").value("焦躁"))
                .andExpect(jsonPath("$.riskLevel").value("L2"))
                .andExpect(jsonPath("$.heartRate").value(88));
    }

    @Test
    void getLatestIncludesRawData() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("perclos", 0.1);
        store.update("dms", data);

        mockMvc.perform(get("/api/v1/drivers/{driverId}/perception/latest", "d007"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverId").value("d007"))
                .andExpect(jsonPath("$.rawData").exists());
    }

    @Test
    void getLatestMapsRiskLevels() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("perclos", 0.1);
        data.put("risk", 3.0);
        store.update("dms", data);

        mockMvc.perform(get("/api/v1/drivers/{driverId}/perception/latest", "d001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskLevel").value("L3"));
    }
}
