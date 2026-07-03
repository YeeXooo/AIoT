package com.aiot.it;

import com.aiot.infra.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.DefaultResponseErrorHandler;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
public abstract class ApiIntegrationTestBase {

    @LocalServerPort
    protected int port;

    @Autowired
    protected JwtTokenProvider jwtTokenProvider;

    @Autowired
    protected TestRestTemplate restTemplate;

    @BeforeEach
    void setUpRestTemplate() {
        restTemplate.getRestTemplate().setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
    }

    protected HttpHeaders authHeaders(String accountId, String role) {
        String token = jwtTokenProvider.createAccessToken(accountId, role);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    protected HttpHeaders familyHeaders() {
        return authHeaders("acct-001-aaa-bbb-ccc-111111111111", "FAMILY");
    }

    protected HttpHeaders managerHeaders() {
        return authHeaders("acct-003-aaa-bbb-ccc-333333333333", "MANAGER");
    }

    protected HttpHeaders familyJsonHeaders() {
        HttpHeaders headers = familyHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }
}
