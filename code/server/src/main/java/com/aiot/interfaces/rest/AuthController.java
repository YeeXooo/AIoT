package com.aiot.interfaces.rest;

import com.aiot.infra.repository.SystemAccountJpaRepository;
import com.aiot.infra.security.JwtTokenProvider;
import com.aiot.infra.security.SecurityProperties;

import io.jsonwebtoken.Claims;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final SystemAccountJpaRepository accountRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final SecurityProperties securityProperties;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

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

        // Fallback: 支持前端用账号名登录（family001/manager001）
        if (accountOpt.isEmpty()) {
            accountOpt = switch (request.credential()) {
                case "family001" -> accountRepository.findByPhone("13900000001");
                case "family002" -> accountRepository.findByPhone("13900000002");
                case "manager001" -> accountRepository.findByPhone("18800000001");
                default -> accountOpt;
            };
        }

        if (accountOpt.isEmpty()) {
            return ResponseEntity.status(401).body(errorBody("AuthFailed", "Invalid credentials"));
        }

        var account = accountOpt.get();
        String passwordHash = account.getPasswordHash();

        if (passwordHash == null || !passwordEncoder.matches(request.secret(), passwordHash)) {
            log.info("登录失败: credential={}", request.credential());
            return ResponseEntity.status(401).body(errorBody("AuthFailed", "Invalid credentials"));
        }

        var tokenPair = jwtTokenProvider.createTokenPair(account.getAccountId(), account.getRole());
        log.info("登录成功: accountId={}, role={}", account.getAccountId(), account.getRole());
        return ResponseEntity.ok(Map.of(
                "accessToken", tokenPair.accessToken(),
                "refreshToken", tokenPair.refreshToken(),
                "tokenType", tokenPair.tokenType(),
                "expiresIn", tokenPair.expiresIn(),
                "accountId", tokenPair.accountId(),
                "role", tokenPair.role(),
                "requiresSecondaryVerification", false
        ));
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
        log.info("secondary-verify 请求: accountId={}, method={}, otp={}",
                request.accountId(), request.method(),
                request.otp() != null ? request.otp().substring(0, Math.min(request.otp().length(), 4)) + "***" : "null");
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
