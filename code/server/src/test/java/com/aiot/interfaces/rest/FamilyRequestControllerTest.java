package com.aiot.interfaces.rest;

import com.aiot.application.PendingFamilyRequestStore;
import com.aiot.application.PendingFamilyRequestStore.FamilyRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class FamilyRequestControllerTest {

    private MockMvc mockMvc;
    private PendingFamilyRequestStore store;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        store = new PendingFamilyRequestStore();
        mockMvc = MockMvcBuilders.standaloneSetup(new FamilyRequestController(store)).build();
    }

    @Test
    void getPendingReturnsNoPendingWhenEmpty() throws Exception {
        mockMvc.perform(get("/api/v1/drivers/{driverId}/family-requests/pending", "d001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPendingRequest").value(false));
    }

    @Test
    void getPendingReturnsRequestWhenPresent() throws Exception {
        FamilyRequest req = FamilyRequest.create("d001", "acc-fam-1", "MEDIA");
        store.put(req);

        mockMvc.perform(get("/api/v1/drivers/{driverId}/family-requests/pending", "d001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPendingRequest").value(true))
                .andExpect(jsonPath("$.accountId").value("acc-fam-1"))
                .andExpect(jsonPath("$.sessionType").value("MEDIA"));
    }

    @Test
    void getPendingOnlyReturnsForGivenDriver() throws Exception {
        FamilyRequest req1 = FamilyRequest.create("d001", "acc-fam-1", "AUDIO");
        FamilyRequest req2 = FamilyRequest.create("d002", "acc-fam-2", "VIDEO");
        store.put(req1);
        store.put(req2);

        mockMvc.perform(get("/api/v1/drivers/{driverId}/family-requests/pending", "d002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPendingRequest").value(true))
                .andExpect(jsonPath("$.accountId").value("acc-fam-2"));
    }

    @Test
    void respondAcceptRemovesRequest() throws Exception {
        FamilyRequest req = FamilyRequest.create("d001", "acc-fam-1", "MEDIA");
        store.put(req);

        String body = mapper.writeValueAsString(Map.of("action", "accept"));
        mockMvc.perform(post("/api/v1/drivers/{driverId}/family-requests/{requestId}/respond",
                                "d001", req.requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolved").value(true))
                .andExpect(jsonPath("$.action").value("accept"));
    }

    @Test
    void respondDeclineDefaultsAction() throws Exception {
        FamilyRequest req = FamilyRequest.create("d001", "acc-fam-1", "MEDIA");
        store.put(req);

        String body = mapper.writeValueAsString(Map.of());
        mockMvc.perform(post("/api/v1/drivers/{driverId}/family-requests/{requestId}/respond",
                                "d001", req.requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("decline"));
    }

    @Test
    void respondNonexistentRequestReturnsResolvedFalse() throws Exception {
        String body = mapper.writeValueAsString(Map.of("action", "accept"));
        mockMvc.perform(post("/api/v1/drivers/{driverId}/family-requests/{requestId}/respond",
                                "d001", "nonexistent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolved").value(false));
    }

    @Test
    void acceptThenPendingIsEmpty() throws Exception {
        FamilyRequest req = FamilyRequest.create("d001", "acc-fam-1", "MEDIA");
        store.put(req);

        String body = mapper.writeValueAsString(Map.of("action", "accept"));
        mockMvc.perform(post("/api/v1/drivers/{driverId}/family-requests/{requestId}/respond",
                                "d001", req.requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/drivers/{driverId}/family-requests/pending", "d001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPendingRequest").value(false));
    }
}
