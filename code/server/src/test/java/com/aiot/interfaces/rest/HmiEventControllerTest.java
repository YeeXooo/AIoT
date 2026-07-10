package com.aiot.interfaces.rest;

import com.aiot.application.HmiEventStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class HmiEventControllerTest {

    private MockMvc mockMvc;
    private HmiEventStore store;

    @BeforeEach
    void setUp() {
        store = new HmiEventStore();
        mockMvc = MockMvcBuilders.standaloneSetup(new HmiEventController(store)).build();
    }

    @Test
    void getPendingReturnsNoPendingWhenEmpty() throws Exception {
        mockMvc.perform(get("/api/v1/drivers/{driverId}/hmi-events/pending", "d001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPending").value(false));
    }

    @Test
    void getPendingReturnsEventWhenPushed() throws Exception {
        store.push("d001", "WARNING", "疲劳警告", "司机疲劳驾驶超过2小时");

        mockMvc.perform(get("/api/v1/drivers/{driverId}/hmi-events/pending", "d001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPending").value(true))
                .andExpect(jsonPath("$.eventType").value("WARNING"))
                .andExpect(jsonPath("$.title").value("疲劳警告"));
    }

    @Test
    void getPendingOnlyReturnsEventsForGivenDriver() throws Exception {
        store.push("d001", "WARNING", "疲劳警告", "d001 事件");
        store.push("d002", "INFO", "系统通知", "d002 事件");

        mockMvc.perform(get("/api/v1/drivers/{driverId}/hmi-events/pending", "d002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPending").value(true))
                .andExpect(jsonPath("$.title").value("系统通知"));
    }

    @Test
    void acknowledgeRemovesEvent() throws Exception {
        var event = store.push("d001", "WARNING", "疲劳警告", "描述");
        String eventId = event.eventId();

        mockMvc.perform(post("/api/v1/drivers/{driverId}/hmi-events/{eventId}/ack", "d001", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acknowledged").value(true));
    }

    @Test
    void acknowledgeNonexistentEventReturnsFalse() throws Exception {
        mockMvc.perform(post("/api/v1/drivers/{driverId}/hmi-events/{eventId}/ack", "d001", "nonexistent-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acknowledged").value(false));
    }

    @Test
    void acknowledgeRemovesOnlyOneEvent() throws Exception {
        var event = store.push("d001", "WARNING", "警告", "描述");

        mockMvc.perform(post("/api/v1/drivers/{driverId}/hmi-events/{eventId}/ack", "d001", event.eventId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acknowledged").value(true));

        mockMvc.perform(get("/api/v1/drivers/{driverId}/hmi-events/pending", "d001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPending").value(false));
    }
}
