package com.aiot.it;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SafetyController API 集成测试")
class SafetyControllerIntegrationTest extends ApiIntegrationTestBase {

    @Test
    @DisplayName("GET /api/v1/safety/trip/list 返回 JSON 数组")
    void listTripsAll() {
        ResponseEntity<String> resp = restTemplate.exchange(
                url("/api/v1/safety/trip/list"), HttpMethod.GET,
                new HttpEntity<>(familyHeaders()), String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().startsWith("["), "应返回 JSON 数组");
    }

    @Test
    @DisplayName("GET /api/v1/safety/trip/list?driverId=X 按司机过滤返回 200")
    void listTripsByDriver() {
        ResponseEntity<String> resp = restTemplate.exchange(
                url("/api/v1/safety/trip/list?driverId=d001-d2e3-4abc-9f01-123456789abc"),
                HttpMethod.GET, new HttpEntity<>(familyHeaders()), String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
    }

    @Test
    @DisplayName("GET /api/v1/safety/trip/list?active=true 仅返回活跃行程")
    void listTripsActiveOnly() {
        ResponseEntity<String> resp = restTemplate.exchange(
                url("/api/v1/safety/trip/list?active=true"),
                HttpMethod.GET, new HttpEntity<>(familyHeaders()), String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
    }

    @Test
    @DisplayName("GET /api/v1/safety/alert/list 返回 JSON 数组")
    void listAlertsAll() {
        ResponseEntity<String> resp = restTemplate.exchange(
                url("/api/v1/safety/alert/list"), HttpMethod.GET,
                new HttpEntity<>(familyHeaders()), String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().startsWith("["), "应返回 JSON 数组");
    }

    @Test
    @DisplayName("GET /api/v1/safety/alert/list?riskLevel=L3_CRITICAL 过滤返回 200")
    void listAlertsByRiskLevel() {
        ResponseEntity<String> resp = restTemplate.exchange(
                url("/api/v1/safety/alert/list?riskLevel=L3_CRITICAL"),
                HttpMethod.GET, new HttpEntity<>(familyHeaders()), String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
    }

    @Test
    @DisplayName("GET /api/v1/safety/alert/list?alertType=FATIGUE 按类型过滤返回 200")
    void listAlertsByType() {
        ResponseEntity<String> resp = restTemplate.exchange(
                url("/api/v1/safety/alert/list?alertType=FATIGUE"),
                HttpMethod.GET, new HttpEntity<>(familyHeaders()), String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
    }

    @Test
    @DisplayName("GET /api/v1/safety/vehicle/list 返回 JSON 数组")
    void listVehiclesAll() {
        ResponseEntity<String> resp = restTemplate.exchange(
                url("/api/v1/safety/vehicle/list"), HttpMethod.GET,
                new HttpEntity<>(familyHeaders()), String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().startsWith("["), "应返回 JSON 数组");
    }

    @Test
    @DisplayName("GET /api/v1/safety/vehicle/list?fleetId=fleet-east-1 按车队过滤返回 200")
    void listVehiclesByFleet() {
        ResponseEntity<String> resp = restTemplate.exchange(
                url("/api/v1/safety/vehicle/list?fleetId=fleet-east-1"),
                HttpMethod.GET, new HttpEntity<>(familyHeaders()), String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
    }

    @Test
    @DisplayName("GET /api/v1/safety/trip/list 无认证返回 401/403")
    void accessWithoutAuth() {
        ResponseEntity<String> resp = restTemplate.getForEntity(
                url("/api/v1/safety/trip/list"), String.class);

        assertTrue(resp.getStatusCode() == HttpStatus.UNAUTHORIZED
                || resp.getStatusCode() == HttpStatus.FORBIDDEN);
    }
}
