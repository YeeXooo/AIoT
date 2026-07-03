package com.aiot.it;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HealthController API 集成测试")
class HealthControllerIntegrationTest extends ApiIntegrationTestBase {

    @Test
    @DisplayName("GET /api/v1/health/{driverId} 查询种子健康档案")
    void getHealthProfile() {
        ResponseEntity<String> resp = restTemplate.exchange(
                url("/api/v1/health/d001-d2e3-4abc-9f01-123456789abc"),
                HttpMethod.GET, new HttpEntity<>(familyHeaders()), String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody().contains("O+"));
        assertTrue(resp.getBody().contains("青霉素过敏"));
    }

    @Test
    @DisplayName("GET /api/v1/health/{driverId} 无认证返回 401/403")
    void getHealthProfileWithoutAuth() {
        ResponseEntity<String> resp = restTemplate.getForEntity(
                url("/api/v1/health/d001-d2e3-4abc-9f01-123456789abc"), String.class);

        assertTrue(resp.getStatusCode() == HttpStatus.UNAUTHORIZED
                || resp.getStatusCode() == HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("GET /api/v1/health/{driverId} 不存在的 ID 返回 null body")
    void getHealthProfileNotFound() {
        ResponseEntity<String> resp = restTemplate.exchange(
                url("/api/v1/health/nonexistent-driver-id"),
                HttpMethod.GET, new HttpEntity<>(familyHeaders()), String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    @DisplayName("PUT /api/v1/health/{driverId} 更新健康档案")
    void updateHealthProfile() {
        String body = """
                {
                    "driverId": "d001-d2e3-4abc-9f01-123456789abc",
                    "bloodType": "B+",
                    "allergyHistory": "无"
                }""";

        ResponseEntity<String> resp = restTemplate.exchange(
                url("/api/v1/health/d001-d2e3-4abc-9f01-123456789abc"),
                HttpMethod.PUT,
                new HttpEntity<>(body, familyJsonHeaders()), String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody().contains("B+"));
    }
}
