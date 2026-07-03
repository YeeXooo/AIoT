package com.aiot.it;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StorageController API 集成测试")
class StorageControllerIntegrationTest extends ApiIntegrationTestBase {

    @Test
    @DisplayName("GET /api/v1/storage/info 返回存储配置信息")
    void storageInfo() {
        ResponseEntity<String> resp = restTemplate.exchange(
                url("/api/v1/storage/info"), HttpMethod.GET,
                new HttpEntity<>(familyHeaders()), String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody().contains("basePath"));
    }

    @Test
    @DisplayName("GET /api/v1/storage/list?dir=voice 列出文件")
    void listFiles() {
        ResponseEntity<String> resp = restTemplate.exchange(
                url("/api/v1/storage/list?dir=voice"), HttpMethod.GET,
                new HttpEntity<>(familyHeaders()), String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    @DisplayName("GET /api/v1/storage/info 无认证返回 401/403")
    void storageInfoWithoutAuth() {
        ResponseEntity<String> resp = restTemplate.getForEntity(
                url("/api/v1/storage/info"), String.class);

        assertTrue(resp.getStatusCode() == HttpStatus.UNAUTHORIZED
                || resp.getStatusCode() == HttpStatus.FORBIDDEN);
    }
}
