package com.aiot.interfaces.rest;

import com.aiot.infra.persistence.SystemAccountJpaEntity;
import com.aiot.infra.repository.SystemAccountJpaRepository;
import com.aiot.infra.security.JwtTokenProvider;
import com.aiot.infra.security.SecurityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private SystemAccountJpaRepository accountRepository;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private SecurityProperties securityProperties;

    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AuthController(accountRepository, jwtTokenProvider, securityProperties)).build();
    }

    private SystemAccountJpaEntity account(String id, String phone, String role, String rawPassword) {
        SystemAccountJpaEntity e = new SystemAccountJpaEntity();
        e.setAccountId(id);
        e.setPhone(phone);
        e.setRole(role);
        e.setPasswordHash(new BCryptPasswordEncoder().encode(rawPassword));
        return e;
    }

    private JwtTokenProvider.TokenPair tokenPair() {
        return new JwtTokenProvider.TokenPair("access-token", "refresh-token", "Bearer", 3600, "acc-1", "FAMILY");
    }

    @Test
    void loginSucceedsWithValidCredentials() throws Exception {
        when(accountRepository.findByPhone("13900000001"))
                .thenReturn(Optional.of(account("acc-1", "13900000001", "FAMILY", "secret123")));
        when(jwtTokenProvider.createTokenPair("acc-1", "FAMILY")).thenReturn(tokenPair());

        String body = mapper.writeValueAsString(
                Map.of("authMethod", "password", "credential", "13900000001", "secret", "secret123"));

        mockMvc.perform(post("/api/v1/auth/login").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.role").value("FAMILY"))
                .andExpect(jsonPath("$.requiresSecondaryVerification").value(false));
    }

    @Test
    void loginSupportsAccountNameFallback() throws Exception {
        when(accountRepository.findByPhone("family001")).thenReturn(Optional.empty());
        when(accountRepository.findByPhone("13900000001"))
                .thenReturn(Optional.of(account("acc-1", "13900000001", "FAMILY", "secret123")));
        when(jwtTokenProvider.createTokenPair("acc-1", "FAMILY")).thenReturn(tokenPair());

        String body = mapper.writeValueAsString(
                Map.of("authMethod", "password", "credential", "family001", "secret", "secret123"));

        mockMvc.perform(post("/api/v1/auth/login").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    @Test
    void loginFailsWhenAccountNotFound() throws Exception {
        when(accountRepository.findByPhone("00000000000")).thenReturn(Optional.empty());

        String body = mapper.writeValueAsString(
                Map.of("authMethod", "password", "credential", "00000000000", "secret", "x"));

        mockMvc.perform(post("/api/v1/auth/login").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AuthFailed"));
    }

    @Test
    void loginFailsWithWrongPassword() throws Exception {
        when(accountRepository.findByPhone("13900000001"))
                .thenReturn(Optional.of(account("acc-1", "13900000001", "FAMILY", "correct")));

        String body = mapper.writeValueAsString(
                Map.of("authMethod", "password", "credential", "13900000001", "secret", "wrong"));

        mockMvc.perform(post("/api/v1/auth/login").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AuthFailed"));
    }

    @Test
    void refreshSucceedsWithValidRefreshToken() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.get("type", String.class)).thenReturn("refresh");
        when(claims.getSubject()).thenReturn("acc-1");
        when(claims.get("role", String.class)).thenReturn("FAMILY");
        when(jwtTokenProvider.validateToken("valid-refresh")).thenReturn(claims);
        when(jwtTokenProvider.createTokenPair("acc-1", "FAMILY")).thenReturn(tokenPair());

        String body = mapper.writeValueAsString(Map.of("refreshToken", "valid-refresh"));

        mockMvc.perform(post("/api/v1/auth/refresh").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    @Test
    void refreshFailsWhenTokenInvalid() throws Exception {
        when(jwtTokenProvider.validateToken("bad")).thenReturn(null);

        String body = mapper.writeValueAsString(Map.of("refreshToken", "bad"));

        mockMvc.perform(post("/api/v1/auth/refresh").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("TokenInvalid"));
    }

    @Test
    void refreshFailsWhenNotRefreshToken() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.get("type", String.class)).thenReturn("access");
        when(jwtTokenProvider.validateToken("access-token")).thenReturn(claims);

        String body = mapper.writeValueAsString(Map.of("refreshToken", "access-token"));

        mockMvc.perform(post("/api/v1/auth/refresh").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("TokenInvalid"));
    }

    @Test
    void secondaryVerifySucceedsWithCorrectOtp() throws Exception {
        when(securityProperties.getMockVerificationCode()).thenReturn("123456");
        when(securityProperties.getMockCodeValiditySeconds()).thenReturn(300);

        String body = mapper.writeValueAsString(
                Map.of("accountId", "acc-1", "method", "SMS", "otp", "123456"));

        mockMvc.perform(post("/api/v1/auth/secondary-verify").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secondaryAuthToken").isString())
                .andExpect(jsonPath("$.expiresAt").isString());
    }

    @Test
    void secondaryVerifyFailsWithWrongOtp() throws Exception {
        when(securityProperties.getMockVerificationCode()).thenReturn("123456");
        when(securityProperties.getMockCodeValiditySeconds()).thenReturn(300);

        String body = mapper.writeValueAsString(
                Map.of("accountId", "acc-1", "method", "SMS", "otp", "999999"));

        mockMvc.perform(post("/api/v1/auth/secondary-verify").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VerificationFailed"));
    }
}
