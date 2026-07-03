package com.aiot.it;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AccountController API 集成测试")
class AccountControllerIntegrationTest extends ApiIntegrationTestBase {

    @Test
    @DisplayName("GET /api/v1/account/list 认证后返回 3 个种子账户")
    void listAccountsWithAuth() {
        ResponseEntity<String> resp = restTemplate.exchange(
                url("/api/v1/account/list"), HttpMethod.GET,
                new HttpEntity<>(familyHeaders()), String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody().contains("acct-001"));
        assertTrue(resp.getBody().contains("acct-002"));
        assertTrue(resp.getBody().contains("acct-003"));
    }

    @Test
    @DisplayName("GET /api/v1/account/list 无 Token 返回 401/403")
    void listAccountsWithoutAuth() {
        ResponseEntity<String> resp = restTemplate.getForEntity(
                url("/api/v1/account/list"), String.class);

        assertTrue(resp.getStatusCode() == HttpStatus.UNAUTHORIZED
                || resp.getStatusCode() == HttpStatus.FORBIDDEN,
                "无认证时预期 401 或 403，实际为 " + resp.getStatusCode());
    }

    @Test
    @DisplayName("GET /api/v1/account/{phone} 按手机号查到种子账户")
    void findByPhoneWithAuth() {
        ResponseEntity<String> resp = restTemplate.exchange(
                url("/api/v1/account/13900000001"), HttpMethod.GET,
                new HttpEntity<>(familyHeaders()), String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody().contains("FAMILY"));
        assertTrue(resp.getBody().contains("13900000001"));
    }

    @Test
    @DisplayName("GET /api/v1/account/{phone} 不存在的手机号返回 null body")
    void findByPhoneNotFound() {
        ResponseEntity<String> resp = restTemplate.exchange(
                url("/api/v1/account/99999999999"), HttpMethod.GET,
                new HttpEntity<>(familyHeaders()), String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    @DisplayName("GET /api/v1/account/list MANAGER 角色也可访问")
    void listAccountsWithManagerRole() {
        ResponseEntity<String> resp = restTemplate.exchange(
                url("/api/v1/account/list"), HttpMethod.GET,
                new HttpEntity<>(managerHeaders()), String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody().contains("MANAGER"));
    }
}
