package com.aiot.interfaces.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sparkrtc")
public class SparkRtcController {

    record TokenRequest(String roomId, String userId, String role) {}

    @PostMapping("/token")
    public ResponseEntity<Map<String, Object>> issueToken(@RequestBody TokenRequest request) {
        String token = UUID.randomUUID().toString();
        String expiresAt = LocalDateTime.now().plusMinutes(10)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z";

        return ResponseEntity.ok(Map.of(
                "token", token,
                "expiresAt", expiresAt
        ));
    }
}
