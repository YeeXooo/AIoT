package com.aiot.interfaces.rest;

import com.aiot.application.GuardianshipApplicationService;
import com.aiot.application.HmiEventStore;
import com.aiot.application.guardianship.IRemoteGuardianshipService;
import com.aiot.infra.persistence.GuardianshipEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class GuardianshipControllerTest {

    @Mock
    private GuardianshipApplicationService service;

    @Mock
    private IRemoteGuardianshipService guardianshipService;

    @Mock
    private HmiEventStore hmiEventStore;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new GuardianshipController(service, guardianshipService, hmiEventStore)).build();
    }

    private static GuardianshipEntity buildEntity(String driverId, String accountId, String reason) {
        GuardianshipEntity e = new GuardianshipEntity();
        e.setDriverId(driverId);
        e.setAccountId(accountId);
        e.setGrantedAt(LocalDateTime.of(2026, 7, 1, 10, 0));
        e.setPermissions("VIEW,MANAGE");
        e.setGrantReason(reason);
        return e;
    }

    @Test
    void listShouldFindByDriverWhenDriverIdProvided() throws Exception {
        GuardianshipEntity e1 = buildEntity("d-001", "acc-001", "family member");
        when(service.findByDriver("d-001")).thenReturn(List.of(e1));

        mockMvc.perform(get("/api/v1/guardianship/list").param("driverId", "d-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].driverId").value("d-001"))
                .andExpect(jsonPath("$[0].accountId").value("acc-001"))
                .andExpect(jsonPath("$[0].permissions").value("VIEW,MANAGE"));

        verify(service).findByDriver("d-001");
        verify(service, never()).findAll();
    }

    @Test
    void listShouldFindAllWhenAccountIdProvided() throws Exception {
        GuardianshipEntity e1 = buildEntity("d-001", "acc-001", "family");
        GuardianshipEntity e2 = buildEntity("d-002", "acc-002", "manager");
        when(service.findAll()).thenReturn(List.of(e1, e2));

        mockMvc.perform(get("/api/v1/guardianship/list").param("accountId", "acc-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        verify(service).findAll();
        verify(service, never()).findByDriver(anyString());
    }

    @Test
    void listShouldFindAllWhenNoParamsProvided() throws Exception {
        when(service.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/guardianship/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(service).findAll();
    }

    @Test
    void listShouldReturnMultipleEntries() throws Exception {
        GuardianshipEntity e1 = buildEntity("d-001", "acc-001", "reason1");
        GuardianshipEntity e2 = buildEntity("d-001", "acc-002", "reason2");
        e2.setRevokedAt(LocalDateTime.of(2026, 7, 2, 10, 0));
        when(service.findByDriver("d-001")).thenReturn(List.of(e1, e2));

        mockMvc.perform(get("/api/v1/guardianship/list").param("driverId", "d-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].driverId").value("d-001"))
                .andExpect(jsonPath("$[0].accountId").value("acc-001"))
                .andExpect(jsonPath("$[1].driverId").value("d-001"))
                .andExpect(jsonPath("$[1].accountId").value("acc-002"));
    }

    @Test
    void createShouldReturnCreatedEntity() throws Exception {
        GuardianshipEntity input = buildEntity("d-new", "acc-new", "emergency");
        GuardianshipEntity saved = buildEntity("d-new", "acc-new", "emergency");
        saved.setGrantedAt(LocalDateTime.of(2026, 7, 3, 15, 0));
        when(service.create(any(GuardianshipEntity.class))).thenReturn(saved);

        mockMvc.perform(post("/api/v1/guardianship")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverId").value("d-new"))
                .andExpect(jsonPath("$.accountId").value("acc-new"))
                .andExpect(jsonPath("$.grantReason").value("emergency"));

        verify(service).create(any(GuardianshipEntity.class));
    }

    @Test
    void createShouldReturn200WithValidJson() throws Exception {
        GuardianshipEntity input = buildEntity("d-json", "acc-json", "test reason");
        GuardianshipEntity result = buildEntity("d-json", "acc-json", "test reason");
        when(service.create(any(GuardianshipEntity.class))).thenReturn(result);

        mockMvc.perform(post("/api/v1/guardianship")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void revokeShouldReturn204NoContent() throws Exception {
        doNothing().when(service).revoke("d-001", "acc-001");

        mockMvc.perform(delete("/api/v1/guardianship/d-001/acc-001"))
                .andExpect(status().isNoContent());

        verify(service).revoke("d-001", "acc-001");
    }

    @Test
    void createShouldHandleEntityWithRevokedAt() throws Exception {
        GuardianshipEntity input = buildEntity("d-rev", "acc-rev", "temporary");
        input.setRevokedAt(LocalDateTime.of(2026, 8, 1, 12, 0));
        when(service.create(any(GuardianshipEntity.class))).thenReturn(input);

        mockMvc.perform(post("/api/v1/guardianship")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revokedAt").isNotEmpty());
    }
}
