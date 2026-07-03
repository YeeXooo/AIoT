package com.aiot.interfaces.websocket;

import com.aiot.infra.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FleetWebSocketHandlerTest {

    @Mock
    private WebSocketSessionRegistry sessionRegistry;
    @Mock
    private WebSocketProperties wsProperties;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private OfflineAlertQueue offlineAlertQueue;
    @Mock
    private WebSocketSession webSocketSession;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private FleetWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        when(wsProperties.getHeartbeatIntervalSec()).thenReturn(3600);
        when(wsProperties.getMaxMissedHeartbeats()).thenReturn(3);
        when(wsProperties.getMaxOfflineAlertsPerReconnect()).thenReturn(20);
        when(wsProperties.getOfflineMessageRetentionDays()).thenReturn(7);

        handler = new FleetWebSocketHandler(sessionRegistry, wsProperties,
                objectMapper, jwtTokenProvider, offlineAlertQueue);
    }

    private void stubManagerAuth() {
        when(jwtTokenProvider.getAccountId(anyString())).thenReturn("mgr-001");
        when(jwtTokenProvider.getRole(anyString())).thenReturn("MANAGER");
    }

    private void stubSession(String sessionId, boolean open, String query) throws Exception {
        when(webSocketSession.getId()).thenReturn(sessionId);
        when(webSocketSession.isOpen()).thenReturn(open);
        URI uri = null;
        if (query != null) {
            uri = new URI("ws://localhost/ws?" + query);
        }
        when(webSocketSession.getUri()).thenReturn(uri);
    }

    @Test
    void afterConnectionEstablished_success() throws Exception {
        stubSession("s1", true, "token=valid-token");
        stubManagerAuth();
        when(sessionRegistry.register("mgr-001", webSocketSession)).thenReturn(null);
        when(offlineAlertQueue.drain("mgr-001")).thenReturn(List.of());

        handler.afterConnectionEstablished(webSocketSession);

        verify(sessionRegistry).register("mgr-001", webSocketSession);
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(webSocketSession, atLeastOnce()).sendMessage(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("connection_established"));
        assertTrue(captor.getValue().getPayload().contains("mgr-001"));
        verify(offlineAlertQueue).drain("mgr-001");
    }

    @Test
    void afterConnectionEstablished_authFailure() throws Exception {
        stubSession("s1", true, "token=bad-token");
        when(jwtTokenProvider.getAccountId(anyString())).thenReturn(null);
        when(jwtTokenProvider.getRole(anyString())).thenReturn(null);

        handler.afterConnectionEstablished(webSocketSession);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(webSocketSession).sendMessage(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("AUTH_FAILED"));
        verify(webSocketSession).close(CloseStatus.NORMAL);
        verify(sessionRegistry, never()).register(anyString(), any());
    }

    @Test
    void afterConnectionEstablished_wrongRole() throws Exception {
        stubSession("s1", true, "token=valid-token");
        when(jwtTokenProvider.getAccountId(anyString())).thenReturn("acc-001");
        when(jwtTokenProvider.getRole(anyString())).thenReturn("FAMILY");

        handler.afterConnectionEstablished(webSocketSession);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(webSocketSession).sendMessage(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("AUTH_FAILED"));
        verify(webSocketSession).close(CloseStatus.NORMAL);
    }

    @Test
    void afterConnectionEstablished_replacesOldSession() throws Exception {
        stubSession("s1", true, "token=valid-token");
        stubManagerAuth();
        WebSocketSession oldSession = mock(WebSocketSession.class);
        when(oldSession.isOpen()).thenReturn(true);
        when(sessionRegistry.register("mgr-001", webSocketSession)).thenReturn(oldSession);
        when(offlineAlertQueue.drain("mgr-001")).thenReturn(List.of());

        handler.afterConnectionEstablished(webSocketSession);

        verify(oldSession).close(CloseStatus.NORMAL);
    }

    @Test
    void afterConnectionEstablished_offlineMessagesDrained() throws Exception {
        stubSession("s1", true, "token=valid-token");
        stubManagerAuth();
        when(sessionRegistry.register("mgr-001", webSocketSession)).thenReturn(null);
        when(offlineAlertQueue.drain("mgr-001")).thenReturn(List.of("{\"type\":\"l3_alert\"}"));

        handler.afterConnectionEstablished(webSocketSession);

        verify(webSocketSession, atLeast(2)).sendMessage(any(TextMessage.class));
    }

    @Test
    void afterConnectionClosed_cleanup() throws Exception {
        stubSession("s1", true, "token=valid-token");
        stubManagerAuth();
        when(sessionRegistry.register("mgr-001", webSocketSession)).thenReturn(null);
        when(offlineAlertQueue.drain("mgr-001")).thenReturn(List.of());
        handler.afterConnectionEstablished(webSocketSession);

        handler.afterConnectionClosed(webSocketSession, CloseStatus.NORMAL);

        verify(sessionRegistry).unregister(webSocketSession);
    }

    @Test
    void handleTextMessage_pong() throws Exception {
        stubSession("s1", true, null);
        when(sessionRegistry.getAccountId("s1")).thenReturn(java.util.Optional.of("mgr-001"));

        clearInvocations(webSocketSession);
        TextMessage msg = new TextMessage("{\"type\":\"pong\"}");
        handler.handleTextMessage(webSocketSession, msg);

        verify(webSocketSession, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void handleTextMessage_subscribeFleet() throws Exception {
        stubSession("s1", true, null);
        when(sessionRegistry.getAccountId("s1")).thenReturn(java.util.Optional.of("mgr-001"));

        TextMessage msg = new TextMessage("{\"type\":\"subscribe_fleet\",\"fleetId\":\"fleet-001\"}");
        assertDoesNotThrow(() -> handler.handleTextMessage(webSocketSession, msg));
    }

    @Test
    void handleTextMessage_subscribeFleet_noFleetId() throws Exception {
        stubSession("s1", true, null);
        when(sessionRegistry.getAccountId("s1")).thenReturn(java.util.Optional.of("mgr-001"));

        TextMessage msg = new TextMessage("{\"type\":\"subscribe_fleet\"}");
        assertDoesNotThrow(() -> handler.handleTextMessage(webSocketSession, msg));
    }

    @Test
    void handleTextMessage_unknownType() throws Exception {
        stubSession("s1", true, null);
        when(sessionRegistry.getAccountId("s1")).thenReturn(java.util.Optional.of("mgr-001"));

        TextMessage msg = new TextMessage("{\"type\":\"bogus\"}");
        handler.handleTextMessage(webSocketSession, msg);

        verify(webSocketSession, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void handleTextMessage_malformedJson() throws Exception {
        stubSession("s1", true, null);
        when(sessionRegistry.getAccountId("s1")).thenReturn(java.util.Optional.of("mgr-001"));

        TextMessage msg = new TextMessage("not-json");
        assertDoesNotThrow(() -> handler.handleTextMessage(webSocketSession, msg));
    }

    @Test
    void handleTransportError_cleanup() throws Exception {
        stubSession("s1", true, "token=valid-token");
        stubManagerAuth();
        when(sessionRegistry.register("mgr-001", webSocketSession)).thenReturn(null);
        when(offlineAlertQueue.drain("mgr-001")).thenReturn(List.of());
        handler.afterConnectionEstablished(webSocketSession);

        handler.handleTransportError(webSocketSession, new RuntimeException("test error"));

        verify(sessionRegistry).unregister(webSocketSession);
    }

    @Test
    void broadcastL3Alert_online() throws Exception {
        WebSocketSession session1 = mock(WebSocketSession.class);
        WebSocketSession session2 = mock(WebSocketSession.class);
        when(session1.isOpen()).thenReturn(true);
        when(session2.isOpen()).thenReturn(true);
        when(session1.getId()).thenReturn("s1");
        when(session2.getId()).thenReturn("s2");
        when(sessionRegistry.getConnectedAccounts()).thenReturn(Set.of("mgr-001", "mgr-002"));
        when(sessionRegistry.getSession("mgr-001")).thenReturn(java.util.Optional.of(session1));
        when(sessionRegistry.getSession("mgr-002")).thenReturn(java.util.Optional.of(session2));

        handler.broadcastL3Alert("fleet-001", "d-001", "v-001", "SOS", Instant.now());

        verify(session1).sendMessage(any(TextMessage.class));
        verify(session2).sendMessage(any(TextMessage.class));
    }

    @Test
    void broadcastL3Alert_offline_enqueuesMessage() throws Exception {
        when(sessionRegistry.getConnectedAccounts()).thenReturn(Set.of("mgr-001"));
        when(sessionRegistry.getSession("mgr-001")).thenReturn(java.util.Optional.empty());

        handler.broadcastL3Alert("fleet-001", "d-001", "v-001", "SOS", Instant.now());

        verify(offlineAlertQueue).enqueue(eq("mgr-001"), anyString());
    }

    @Test
    void broadcastPerformanceWarning_online() throws Exception {
        WebSocketSession session1 = mock(WebSocketSession.class);
        when(session1.isOpen()).thenReturn(true);
        when(session1.getId()).thenReturn("s1");
        when(sessionRegistry.getConnectedAccounts()).thenReturn(Set.of("mgr-001"));
        when(sessionRegistry.getSession("mgr-001")).thenReturn(java.util.Optional.of(session1));

        handler.broadcastPerformanceWarning("d-001", "张三", 55, "2026-Q1",
                List.of("疲劳驾驶", "超速"));

        verify(session1).sendMessage(any(TextMessage.class));
    }

    @Test
    void sendMessage_sendFails_gracefullyHandlesIOException() throws Exception {
        stubSession("s1", true, null);
        when(sessionRegistry.getConnectedAccounts()).thenReturn(Set.of("mgr-001"));
        when(sessionRegistry.getSession("mgr-001")).thenReturn(java.util.Optional.of(webSocketSession));
        when(webSocketSession.isOpen()).thenReturn(true);
        doThrow(new IOException("send failed")).when(webSocketSession).sendMessage(any(TextMessage.class));

        assertDoesNotThrow(() ->
                handler.broadcastL3Alert("fleet-001", "d-001", "v-001", "SOS", Instant.now()));
    }
}
