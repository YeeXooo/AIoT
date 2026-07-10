package com.aiot.interfaces.rest;

import com.aiot.application.LatestSensorDataStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class YoloFeedControllerTest {

    private MockMvc mockMvc;
    private LatestSensorDataStore store;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        store = new LatestSensorDataStore();
        mockMvc = MockMvcBuilders.standaloneSetup(new YoloFeedController(store)).build();
    }

    private Map<String, Object> sampleFrame() {
        Map<String, Object> frame = new HashMap<>();
        frame.put("perclos", 0.42);
        frame.put("yawn_freq", 3.0);
        frame.put("head_nod_freq", 5.0);
        frame.put("gaze_deviation_cumulative", 8.0);
        frame.put("hands_off_wheel", 0.3);
        frame.put("confidence", 0.9);
        frame.put("phone_detected", 0.0);
        frame.put("smoking_detected", 0.0);
        frame.put("frame_seq", 42);
        frame.put("timestamp_ms", 1700000000000L);
        return frame;
    }

    @Test
    void feedReturnsOkStatus() throws Exception {
        String body = mapper.writeValueAsString(sampleFrame());
        mockMvc.perform(post("/api/v1/perception/yolo-feed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.frameSeq").value(42));
    }

    @Test
    void feedUpdatesSensorDataStore() throws Exception {
        String body = mapper.writeValueAsString(sampleFrame());
        mockMvc.perform(post("/api/v1/perception/yolo-feed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        Map<String, Object> latest = store.getLatest();
        assertEquals(true, latest.get("yolo_active"));
        assertEquals(0.42, latest.get("yolo_perclos"));
        assertEquals(42, latest.get("yolo_frame_seq"));
    }

    @Test
    void feedReturnsFrameSeqFromRequest() throws Exception {
        String body = mapper.writeValueAsString(sampleFrame());

        mockMvc.perform(post("/api/v1/perception/yolo-feed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frameSeq").value(42));
    }

    @Test
    void feedMergesWithExistingData() throws Exception {
        Map<String, Object> existing = new HashMap<>();
        existing.put("hr", 80);
        store.update("dms", existing);

        String body = mapper.writeValueAsString(sampleFrame());
        mockMvc.perform(post("/api/v1/perception/yolo-feed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        Map<String, Object> latest = store.getLatest();
        assertEquals(80, latest.get("hr"));
        assertEquals(true, latest.get("yolo_active"));
    }

    @Test
    void feedWithMinimalFrameStillReturnsOk() throws Exception {
        Map<String, Object> frame = new HashMap<>();
        frame.put("perclos", 0.0);
        frame.put("yawn_freq", 0.0);
        frame.put("head_nod_freq", 0.0);
        frame.put("gaze_deviation_cumulative", 0.0);
        frame.put("hands_off_wheel", 0.0);
        frame.put("confidence", 0.0);
        frame.put("phone_detected", 0.0);
        frame.put("smoking_detected", 0.0);
        frame.put("frame_seq", 1);
        frame.put("timestamp_ms", 1700000000000L);
        String body = mapper.writeValueAsString(frame);

        mockMvc.perform(post("/api/v1/perception/yolo-feed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }
}
