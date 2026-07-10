package com.aiot.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class SparkRtcControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SparkRtcController()).build();
    }

    @Test
    void issueTokenReturnsOk() throws Exception {
        String body = mapper.writeValueAsString(Map.of("roomId", "room-1", "userId", "user-1", "role", "presenter"));
        mockMvc.perform(post("/api/v1/sparkrtc/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void issueTokenReturnsTokenField() throws Exception {
        String body = mapper.writeValueAsString(Map.of("roomId", "room-1", "userId", "user-1", "role", "presenter"));
        mockMvc.perform(post("/api/v1/sparkrtc/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void issueTokenReturnsExpiresAt() throws Exception {
        String body = mapper.writeValueAsString(Map.of("roomId", "room-1", "userId", "user-1", "role", "presenter"));
        mockMvc.perform(post("/api/v1/sparkrtc/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").isString())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
    }

    @Test
    void issueTokenWithMinimalRequest() throws Exception {
        String body = mapper.writeValueAsString(Map.of("roomId", "r2", "userId", "u2", "role", "viewer"));
        String response = mockMvc.perform(post("/api/v1/sparkrtc/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(response.contains("token"));
        assertTrue(response.contains("expiresAt"));
    }
}
