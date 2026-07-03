package com.aiot.infra.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
    }

    @Test
    void passesThroughNonApiPath() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/health");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void withInLimitPassesThrough() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/api/v1/health");
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void exceedsLimitReturns429() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/api/v1/health");
        when(request.getRemoteAddr()).thenReturn("192.168.1.2");
        when(response.getWriter()).thenReturn(mock(PrintWriter.class));

        for (int i = 0; i < 20; i++) {
            filter.doFilter(request, response, filterChain);
        }

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(20)).doFilter(request, response);
        verify(response, times(1)).setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    void authEndpointHasLowerLimit() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(request.getRemoteAddr()).thenReturn("192.168.1.3");
        when(response.getWriter()).thenReturn(mock(PrintWriter.class));

        for (int i = 0; i < 6; i++) {
            filter.doFilter(request, response, filterChain);
        }

        verify(filterChain, times(5)).doFilter(request, response);
        verify(response, times(1)).setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    void extractsAccountIdFromAuthorizationHeader() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/api/v1/health");
        when(request.getHeader("Authorization"))
                .thenReturn("Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.dGVzdA.abcdef");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void usesRemoteAddrWhenNoAuthHeader() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/api/v1/health");
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void differentIpsHaveSeparateLimits() throws ServletException, IOException {
        HttpServletRequest request2 = mock(HttpServletRequest.class);

        when(request.getRequestURI()).thenReturn("/api/v1/health");
        when(request.getRemoteAddr()).thenReturn("192.168.1.10");

        when(request2.getRequestURI()).thenReturn("/api/v1/health");
        when(request2.getRemoteAddr()).thenReturn("192.168.1.11");

        for (int i = 0; i < 20; i++) {
            filter.doFilter(request, response, filterChain);
        }

        filter.doFilter(request2, response, filterChain);

        verify(filterChain, times(20)).doFilter(request, response);
        verify(filterChain, times(1)).doFilter(request2, response);
    }

    @Test
    void truncatesLongAuthorizationHeader() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/api/v1/health");
        when(request.getHeader("Authorization")).thenReturn(
                "Bearer " + "a".repeat(200));

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void setsJsonContentTypeOnRateLimited() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/api/v1/health");
        when(request.getRemoteAddr()).thenReturn("192.168.1.20");
        when(response.getWriter()).thenReturn(mock(PrintWriter.class));

        for (int i = 0; i < 20; i++) {
            filter.doFilter(request, response, filterChain);
        }

        filter.doFilter(request, response, filterChain);

        verify(response).setContentType("application/json");
    }
}
