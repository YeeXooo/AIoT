package com.aiot.interfaces.rest;

import com.aiot.infra.repository.SystemAccountJpaRepository;
import com.aiot.infra.security.JwtTokenProvider;
import com.aiot.infra.security.SecurityProperties;

import io.jsonwebtoken.Claims;

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
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final SystemAccountJpaRepository accountRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final SecurityProperties securityProperties;

    public AuthController(SystemAccountJpaRepository accountRepository,
                          JwtTokenProvider jwtTokenProvider,
                          SecurityProperties securityProperties) {
        this.accountRepository = accountRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.securityProperties = securityProperties;
    }

    record LoginRequest(String authMethod, String credential, String secret) {}
    record RefreshRequest(String refreshToken) {}
    record SecondaryVerifyRequest(String accountId, String method, String otp) {}

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        var accountOpt = accountRepository.findByPhone(request.credential());
        if (accountOpt.isEmpty()) {
            return ResponseEntity.status(401).body(errorBody("AuthFailed", "Invalid credentials"));
        }

        var account = accountOpt.get();
        var tokenPair = jwtTokenProvider.createTokenPair(account.getAccountId(), account.getRole());

        return ResponseEntity.ok(tokenPair);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest request) {
        Claims claims = jwtTokenProvider.validateToken(request.refreshToken());
        if (claims == null) {
            return ResponseEntity.status(401).body(errorBody("TokenInvalid", "Invalid or expired refresh token"));
        }

        String type = claims.get("type", String.class);
        if (!"refresh".equals(type)) {
            return ResponseEntity.status(401).body(errorBody("TokenInvalid", "Not a refresh token"));
        }

        String accountId = claims.getSubject();
        String role = claims.get("role", String.class);

        var tokenPair = jwtTokenProvider.createTokenPair(accountId, role);

        return ResponseEntity.ok(Map.of(
                "accessToken", tokenPair.accessToken(),
                "refreshToken", tokenPair.refreshToken(),
                "tokenType", tokenPair.tokenType(),
                "expiresIn", tokenPair.expiresIn()
        ));
    }

    @PostMapping("/secondary-verify")
    public ResponseEntity<?> secondaryVerify(@RequestBody SecondaryVerifyRequest request) {
        String mockCode = securityProperties.getMockVerificationCode();
        int validitySec = securityProperties.getMockCodeValiditySeconds();

        String otp = request.otp();
        if (otp == null || !otp.equals(mockCode)) {
            return ResponseEntity.status(400).body(errorBody("VerificationFailed", "Invalid verification code"));
        }

        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(validitySec);
        String secondaryAuthToken = "secondary-" + UUID.randomUUID().toString();

        return ResponseEntity.ok(Map.of(
                "secondaryAuthToken", secondaryAuthToken,
                "expiresAt", expiresAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z"
        ));
    }

    private static Map<String, Object> errorBody(String errorCode, String message) {
        return Map.of("errorCode", errorCode, "message", message,
                "requestId", UUID.randomUUID().toString());
    }
}
