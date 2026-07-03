package com.aiot.interfaces.rest;

import com.aiot.infra.persistence.SystemAccountJpaEntity;
import com.aiot.infra.repository.SystemAccountJpaRepository;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    @Mock
    private SystemAccountJpaRepository accountRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AccountController(accountRepository)).build();
    }

    @Test
    void listReturnsAllAccounts() throws Exception {
        SystemAccountJpaEntity entity = new SystemAccountJpaEntity();
        entity.setAccountId("acc-1");
        entity.setPhone("13800138000");
        entity.setRole("ADMIN");
        when(accountRepository.findAll()).thenReturn(List.of(entity));

        mockMvc.perform(get("/api/v1/account/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountId").value("acc-1"))
                .andExpect(jsonPath("$[0].phone").value("13800138000"))
                .andExpect(jsonPath("$[0].role").value("ADMIN"));
    }

    @Test
    void listReturnsMultipleAccounts() throws Exception {
        SystemAccountJpaEntity e1 = new SystemAccountJpaEntity();
        e1.setAccountId("acc-1");
        e1.setPhone("13800138000");
        e1.setRole("ADMIN");
        SystemAccountJpaEntity e2 = new SystemAccountJpaEntity();
        e2.setAccountId("acc-2");
        e2.setPhone("13900139000");
        e2.setRole("USER");
        when(accountRepository.findAll()).thenReturn(List.of(e1, e2));

        mockMvc.perform(get("/api/v1/account/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void listReturnsEmptyListWhenNoAccounts() throws Exception {
        when(accountRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/account/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listWhenRepositoryThrowsException() {
        when(accountRepository.findAll()).thenThrow(new RuntimeException("DB error"));

        ServletException ex = assertThrows(ServletException.class, () -> {
            mockMvc.perform(get("/api/v1/account/list"));
        });
        assertTrue(ex.getCause() instanceof RuntimeException);
        assertEquals("DB error", ex.getCause().getMessage());
    }

    @Test
    void findByPhoneReturnsAccountWhenFound() throws Exception {
        SystemAccountJpaEntity entity = new SystemAccountJpaEntity();
        entity.setAccountId("acc-1");
        entity.setPhone("13800138000");
        entity.setRole("ADMIN");
        when(accountRepository.findByPhone("13800138000")).thenReturn(Optional.of(entity));

        mockMvc.perform(get("/api/v1/account/{phone}", "13800138000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("acc-1"))
                .andExpect(jsonPath("$.phone").value("13800138000"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void findByPhoneReturnsNullWhenNotFound() throws Exception {
        when(accountRepository.findByPhone("99999999999")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/account/{phone}", "99999999999"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    void findByPhoneWhenRepositoryThrowsException() {
        when(accountRepository.findByPhone(anyString())).thenThrow(new RuntimeException("DB error"));

        ServletException ex = assertThrows(ServletException.class, () -> {
            mockMvc.perform(get("/api/v1/account/{phone}", "13800138000"));
        });
        assertTrue(ex.getCause() instanceof RuntimeException);
        assertEquals("DB error", ex.getCause().getMessage());
    }

    @Test
    void findByPhoneWithSpecialCharacters() throws Exception {
        when(accountRepository.findByPhone("+86-138")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/account/{phone}", "+86-138"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }
}
