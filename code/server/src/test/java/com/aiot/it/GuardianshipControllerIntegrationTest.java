package com.aiot.it;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GuardianshipController API 集成测试")
class GuardianshipControllerIntegrationTest extends ApiIntegrationTestBase {

    @Test
    @DisplayName("GET /api/v1/guardianship/list FAMILY 角色返回监护关系")
    void listGuardianshipsAsFamily() {
        ResponseEntity<String> resp = restTemplate.exchange(
                url("/api/v1/guardianship/list"), HttpMethod.GET,
                new HttpEntity<>(familyHeaders()), String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody().contains("常规监护"));
    }

    @Test
    @DisplayName("GET /api/v1/guardianship/list?driverId=X 按司机过滤")
    void listGuardianshipsByDriver() {
        ResponseEntity<String> resp = restTemplate.exchange(
                url("/api/v1/guardianship/list?driverId=d001-d2e3-4abc-9f01-123456789abc"),
                HttpMethod.GET, new HttpEntity<>(familyHeaders()), String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody().contains("acct-001"));
    }

    @Test
    @DisplayName("GET /api/v1/guardianship/list MANAGER 角色被拒绝（需要 FAMILY）")
    void listGuardianshipsAsManagerDenied() {
        ResponseEntity<String> resp = restTemplate.exchange(
                url("/api/v1/guardianship/list"), HttpMethod.GET,
                new HttpEntity<>(managerHeaders()), String.class);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    @DisplayName("GET /api/v1/guardianship/list 无认证返回 401/403")
    void listWithoutAuth() {
        ResponseEntity<String> resp = restTemplate.getForEntity(
                url("/api/v1/guardianship/list"), String.class);

        assertTrue(resp.getStatusCode() == HttpStatus.UNAUTHORIZED
                || resp.getStatusCode() == HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("POST /api/v1/guardianship 创建新监护关系")
    void createGuardianship() {
        String body = """
                {
                    "driverId": "d005-b6d4-8ef0-5b05-567890123fgh",
                    "accountId": "acct-001-aaa-bbb-ccc-111111111111",
                    "permissions": "{\\"can_view_alert\\":true}",
                    "grantReason": "test"
                }""";

        ResponseEntity<String> resp = restTemplate.exchange(
                url("/api/v1/guardianship"), HttpMethod.POST,
                new HttpEntity<>(body, familyJsonHeaders()), String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody().contains("d005"));
    }

    @Test
    @DisplayName("DELETE /api/v1/guardianship/{driverId}/{accountId} 撤销监护关系")
    void revokeGuardianship() {
        String driverId = "d003-e4b2-6cde-7d03-345678901def";
        String accountId = "acct-002-aaa-bbb-ccc-222222222222";

        ResponseEntity<Void> resp = restTemplate.exchange(
                url("/api/v1/guardianship/" + driverId + "/" + accountId),
                HttpMethod.DELETE,
                new HttpEntity<>(familyHeaders()), Void.class);

        assertEquals(HttpStatus.NO_CONTENT, resp.getStatusCode());
    }
}
