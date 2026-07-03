package com.aiot.it;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProjectionController API 集成测试")
class ProjectionControllerIntegrationTest extends ApiIntegrationTestBase {

    @Test
    @DisplayName("GET /api/v1/projection/alert 返回种子告警投影")
    void listAlertProjections() {
        ResponseEntity<String> resp = restTemplate.exchange(
                url("/api/v1/projection/alert"), HttpMethod.GET,
                new HttpEntity<>(familyHeaders()), String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody().contains("FATIGUE"));
    }

    @Test
    @DisplayName("GET /api/v1/projection/alert?fleetId=fleet-east-1 按车队过滤")
    void listAlertProjectionsByFleet() {
        ResponseEntity<String> resp = restTemplate.exchange(
                url("/api/v1/projection/alert?fleetId=fleet-east-1"),
                HttpMethod.GET, new HttpEntity<>(familyHeaders()), String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody().contains("fleet-east-1"));
    }

    @Test
    @DisplayName("GET /api/v1/projection/dashboard 返回看板投影")
    void listDashboardProjections() {
        ResponseEntity<String> resp = restTemplate.exchange(
                url("/api/v1/projection/dashboard"), HttpMethod.GET,
                new HttpEntity<>(familyHeaders()), String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    @DisplayName("GET /api/v1/projection/dashboard?fleetId=fleet-east-1 按车队过滤看板")
    void listDashboardProjectionsByFleet() {
        ResponseEntity<String> resp = restTemplate.exchange(
                url("/api/v1/projection/dashboard?fleetId=fleet-east-1"),
                HttpMethod.GET, new HttpEntity<>(familyHeaders()), String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody().contains("fleet-east-1"));
    }

    @Test
    @DisplayName("GET /api/v1/projection/trajectory?tripId=X 返回轨迹投影")
    void listTrajectoryProjections() {
        ResponseEntity<String> resp = restTemplate.exchange(
                url("/api/v1/projection/trajectory?tripId=t001-12ab-34cd-56ef-78901234abcd"),
                HttpMethod.GET, new HttpEntity<>(familyHeaders()), String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody().contains("39.9042"));
    }

    @Test
    @DisplayName("GET /api/v1/projection/alert 无认证返回 401/403")
    void accessWithoutAuth() {
        ResponseEntity<String> resp = restTemplate.getForEntity(
                url("/api/v1/projection/alert"), String.class);

        assertTrue(resp.getStatusCode() == HttpStatus.UNAUTHORIZED
                || resp.getStatusCode() == HttpStatus.FORBIDDEN);
    }
}
