package com.aiot.it;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DriverController API 集成测试")
class DriverControllerIntegrationTest extends ApiIntegrationTestBase {

    @Test
    @DisplayName("GET /api/v1/driver/list 返回种子驾驶员 JSON 数组")
    void listAllDrivers() {
        ResponseEntity<String> resp = restTemplate.exchange(
                url("/api/v1/driver/list"), HttpMethod.GET,
                new HttpEntity<>(familyHeaders()), String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        String body = resp.getBody();
        assertNotNull(body, "响应体不应为 null");
        assertTrue(body.startsWith("[") && body.endsWith("]"),
                "应返回 JSON 数组，实际: " + (body.length() > 100 ? body.substring(0, 100) : body));
    }

    @Test
    @DisplayName("GET /api/v1/driver/list?name=张 有认证返回 200")
    void listDriversByNameFilter() {
        ResponseEntity<String> resp = restTemplate.exchange(
                url("/api/v1/driver/list?name=张"), HttpMethod.GET,
                new HttpEntity<>(familyHeaders()), String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
    }

    @Test
    @DisplayName("GET /api/v1/driver/list 无认证返回 401/403")
    void listWithoutAuth() {
        ResponseEntity<String> resp = restTemplate.getForEntity(
                url("/api/v1/driver/list"), String.class);

        assertTrue(resp.getStatusCode() == HttpStatus.UNAUTHORIZED
                || resp.getStatusCode() == HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("POST /api/v1/driver 无认证返回 401/403")
    void addWithoutAuth() {
        String body = "{\"driverId\":{\"id\":\"\"},\"name\":\"test\",\"phone\":\"12345678901\"}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> resp = restTemplate.exchange(
                url("/api/v1/driver"), HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);

        assertTrue(resp.getStatusCode() == HttpStatus.UNAUTHORIZED
                || resp.getStatusCode() == HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("DELETE /api/v1/driver/{id} 无认证返回 401/403")
    void deleteWithoutAuth() {
        ResponseEntity<String> resp = restTemplate.exchange(
                url("/api/v1/driver/d001-d2e3-4abc-9f01-123456789abc"),
                HttpMethod.DELETE, new HttpEntity<>(new HttpHeaders()), String.class);

        assertTrue(resp.getStatusCode() == HttpStatus.UNAUTHORIZED
                || resp.getStatusCode() == HttpStatus.FORBIDDEN);
    }
}
