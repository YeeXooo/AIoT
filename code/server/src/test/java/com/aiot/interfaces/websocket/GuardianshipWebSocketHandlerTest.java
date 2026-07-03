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
class GuardianshipWebSocketHandlerTest {

    @Mock
    private WebSocketSessionRegistry sessionRegistry;
    @Mock
    private WebSocketProperties wsProperties;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private MediaSessionManager mediaSessionManager;
    @Mock
    private OfflineAlertQueue offlineAlertQueue;
    @Mock
    private WebSocketSession webSocketSession;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private GuardianshipWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        when(wsProperties.getHeartbeatIntervalSec()).thenReturn(3600);
        when(wsProperties.getMaxMissedHeartbeats()).thenReturn(3);
        when(wsProperties.getMaxSubscriptionsPerDriver()).thenReturn(3);
        when(wsProperties.getMaxOfflineAlertsPerReconnect()).thenReturn(20);
        when(wsProperties.getOfflineMessageRetentionDays()).thenReturn(7);

        handler = new GuardianshipWebSocketHandler(sessionRegistry, wsProperties,
                objectMapper, jwtTokenProvider, mediaSessionManager, offlineAlertQueue);
    }

    private void stubFamilyAuth() {
        when(jwtTokenProvider.getAccountId(anyString())).thenReturn("acc-001");
        when(jwtTokenProvider.getRole(anyString())).thenReturn("FAMILY");
    }

    private void stubAuth(String accountId, String role) {
        when(jwtTokenProvider.getAccountId(anyString())).thenReturn(accountId);
        when(jwtTokenProvider.getRole(anyString())).thenReturn(role);
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
        stubFamilyAuth();
        when(sessionRegistry.register("acc-001", webSocketSession)).thenReturn(null);
        when(offlineAlertQueue.drain("acc-001")).thenReturn(List.of());

        handler.afterConnectionEstablished(webSocketSession);

        verify(sessionRegistry).register("acc-001", webSocketSession);
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(webSocketSession, atLeastOnce()).sendMessage(captor.capture());
        String payload = captor.getValue().getPayload();
        assertTrue(payload.contains("connection_established"));
        assertTrue(payload.contains("acc-001"));
        verify(offlineAlertQueue).drain("acc-001");
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
        stubAuth("acc-001", "DRIVER");

        handler.afterConnectionEstablished(webSocketSession);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(webSocketSession).sendMessage(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("AUTH_FAILED"));
        verify(webSocketSession).close(CloseStatus.NORMAL);
    }

    @Test
    void afterConnectionEstablished_replacesOldSession() throws Exception {
        stubSession("s1", true, "token=valid-token");
        stubFamilyAuth();
        WebSocketSession oldSession = mock(WebSocketSession.class);
        when(oldSession.isOpen()).thenReturn(true);
        when(sessionRegistry.register("acc-001", webSocketSession)).thenReturn(oldSession);
        when(offlineAlertQueue.drain("acc-001")).thenReturn(List.of());

        handler.afterConnectionEstablished(webSocketSession);

        verify(oldSession).close(CloseStatus.NORMAL);
    }

    @Test
    void afterConnectionEstablished_noUri() throws Exception {
        stubSession("s1", true, null);

        handler.afterConnectionEstablished(webSocketSession);

        verify(webSocketSession).close(CloseStatus.NORMAL);
        verify(sessionRegistry, never()).register(anyString(), any());
    }

    @Test
    void afterConnectionEstablished_offlineMessagesDrained() throws Exception {
        stubSession("s1", true, "token=valid-token");
        stubFamilyAuth();
        when(sessionRegistry.register("acc-001", webSocketSession)).thenReturn(null);
        when(offlineAlertQueue.drain("acc-001")).thenReturn(List.of("{\"type\":\"alert_triggered\"}"));

        handler.afterConnectionEstablished(webSocketSession);

        verify(webSocketSession, atLeast(2)).sendMessage(any(TextMessage.class));
        verify(offlineAlertQueue).drain("acc-001");
    }

    @Test
    void afterConnectionClosed_cleanup() throws Exception {
        stubSession("s1", true, "token=valid-token");
        stubFamilyAuth();
        when(sessionRegistry.register("acc-001", webSocketSession)).thenReturn(null);
        when(offlineAlertQueue.drain("acc-001")).thenReturn(List.of());
        handler.afterConnectionEstablished(webSocketSession);

        handler.afterConnectionClosed(webSocketSession, CloseStatus.NORMAL);

        verify(sessionRegistry).unregister(webSocketSession);
    }

    @Test
    void handleTextMessage_subscribeStatus_success() throws Exception {
        stubSession("s1", true, null);
        when(sessionRegistry.getAccountId("s1")).thenReturn(java.util.Optional.of("acc-001"));
        when(sessionRegistry.subscribe("d-001", "acc-001", 3)).thenReturn(true);

        TextMessage msg = new TextMessage("{\"type\":\"subscribe_status\",\"driverId\":\"d-001\"}");
        handler.handleTextMessage(webSocketSession, msg);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(webSocketSession).sendMessage(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("subscribe_status_ack"));
    }

    @Test
    void handleTextMessage_subscribeStatus_missingDriverId() throws Exception {
        stubSession("s1", true, null);
        when(sessionRegistry.getAccountId("s1")).thenReturn(java.util.Optional.of("acc-001"));

        TextMessage msg = new TextMessage("{\"type\":\"subscribe_status\"}");
        handler.handleTextMessage(webSocketSession, msg);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(webSocketSession).sendMessage(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("INVALID_REQUEST"));
    }

    @Test
    void handleTextMessage_subscribeStatus_limitReached() throws Exception {
        stubSession("s1", true, null);
        when(sessionRegistry.getAccountId("s1")).thenReturn(java.util.Optional.of("acc-001"));
        when(sessionRegistry.subscribe("d-001", "acc-001", 3)).thenReturn(false);

        TextMessage msg = new TextMessage("{\"type\":\"subscribe_status\",\"driverId\":\"d-001\"}");
        handler.handleTextMessage(webSocketSession, msg);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(webSocketSession).sendMessage(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("SUBSCRIPTION_LIMIT"));
    }

    @Test
    void handleTextMessage_unsubscribeStatus() throws Exception {
        stubSession("s1", true, null);
        when(sessionRegistry.getAccountId("s1")).thenReturn(java.util.Optional.of("acc-001"));

        TextMessage msg = new TextMessage("{\"type\":\"unsubscribe_status\",\"driverId\":\"d-001\"}");
        handler.handleTextMessage(webSocketSession, msg);

        verify(sessionRegistry).unsubscribe("d-001", "acc-001");
    }

    @Test
    void handleTextMessage_requestMedia() throws Exception {
        stubSession("s1", true, null);
        when(sessionRegistry.getAccountId("s1")).thenReturn(java.util.Optional.of("acc-001"));
        MediaSessionManager.MediaSession mediaSession = new MediaSessionManager.MediaSession(
                "session-1", "acc-001", "d-001", "AUDIO",
                "room-1", "join-token", "media-token",
                Instant.now(), Instant.now().plusSeconds(600),
                MediaSessionManager.MediaSessionState.ACTIVE);
        when(mediaSessionManager.createSession("acc-001", "d-001", "VIDEO")).thenReturn(mediaSession);

        TextMessage msg = new TextMessage("{\"type\":\"request_media\",\"driverId\":\"d-001\",\"sessionType\":\"VIDEO\"}");
        handler.handleTextMessage(webSocketSession, msg);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(webSocketSession).sendMessage(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("access_granted"));
        assertTrue(captor.getValue().getPayload().contains("session-1"));
    }

    @Test
    void handleTextMessage_requestMedia_defaults() throws Exception {
        stubSession("s1", true, null);
        when(sessionRegistry.getAccountId("s1")).thenReturn(java.util.Optional.of("acc-001"));
        MediaSessionManager.MediaSession mediaSession = new MediaSessionManager.MediaSession(
                "session-2", "acc-001", "", "AUDIO",
                "room-2", "join-2", "media-2",
                Instant.now(), Instant.now().plusSeconds(600),
                MediaSessionManager.MediaSessionState.ACTIVE);
        when(mediaSessionManager.createSession("acc-001", "", "AUDIO")).thenReturn(mediaSession);

        TextMessage msg = new TextMessage("{\"type\":\"request_media\"}");
        handler.handleTextMessage(webSocketSession, msg);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(webSocketSession).sendMessage(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("access_granted"));
    }

    @Test
    void handleTextMessage_endMedia() throws Exception {
        stubSession("s1", true, null);
        when(sessionRegistry.getAccountId("s1")).thenReturn(java.util.Optional.of("acc-001"));

        TextMessage msg = new TextMessage("{\"type\":\"end_media\",\"sessionHandle\":\"session-1\"}");
        handler.handleTextMessage(webSocketSession, msg);

        verify(mediaSessionManager).endSession("session-1");
    }

    @Test
    void handleTextMessage_renewToken_success() throws Exception {
        stubSession("s1", true, null);
        when(sessionRegistry.getAccountId("s1")).thenReturn(java.util.Optional.of("acc-001"));
        MediaSessionManager.TokenRenewal renewal = new MediaSessionManager.TokenRenewal(true, "room-1", "new-token", Instant.now(), null);
        when(mediaSessionManager.renewToken("session-1")).thenReturn(renewal);

        TextMessage msg = new TextMessage("{\"type\":\"renew_token\",\"sessionHandle\":\"session-1\"}");
        handler.handleTextMessage(webSocketSession, msg);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(webSocketSession).sendMessage(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("token_renewed"));
    }

    @Test
    void handleTextMessage_renewToken_failure() throws Exception {
        stubSession("s1", true, null);
        when(sessionRegistry.getAccountId("s1")).thenReturn(java.util.Optional.of("acc-001"));
        MediaSessionManager.TokenRenewal renewal = MediaSessionManager.TokenRenewal.failed("SESSION_NOT_FOUND");
        when(mediaSessionManager.renewToken("session-1")).thenReturn(renewal);

        TextMessage msg = new TextMessage("{\"type\":\"renew_token\",\"sessionHandle\":\"session-1\"}");
        handler.handleTextMessage(webSocketSession, msg);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(webSocketSession).sendMessage(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("TOKEN_RENEW_FAILED"));
    }

    @Test
    void handleTextMessage_triggerRescue() throws Exception {
        stubSession("s1", true, null);
        when(sessionRegistry.getAccountId("s1")).thenReturn(java.util.Optional.of("acc-001"));

        TextMessage msg = new TextMessage("{\"type\":\"trigger_rescue\",\"driverId\":\"d-001\"}");
        handler.handleTextMessage(webSocketSession, msg);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(webSocketSession).sendMessage(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("rescue_triggered"));
        assertTrue(captor.getValue().getPayload().contains("PENDING"));
    }

    @Test
    void handleTextMessage_pong() throws Exception {
        stubSession("s1", true, null);
        when(sessionRegistry.getAccountId("s1")).thenReturn(java.util.Optional.of("acc-001"));

        clearInvocations(webSocketSession);
        TextMessage msg = new TextMessage("{\"type\":\"pong\"}");
        handler.handleTextMessage(webSocketSession, msg);

        verify(webSocketSession, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void handleTextMessage_unknownType() throws Exception {
        stubSession("s1", true, null);
        when(sessionRegistry.getAccountId("s1")).thenReturn(java.util.Optional.of("acc-001"));

        TextMessage msg = new TextMessage("{\"type\":\"bogus\"}");
        handler.handleTextMessage(webSocketSession, msg);

        verify(webSocketSession, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void handleTextMessage_malformedJson() throws Exception {
        stubSession("s1", true, null);
        when(sessionRegistry.getAccountId("s1")).thenReturn(java.util.Optional.of("acc-001"));

        TextMessage msg = new TextMessage("not-json");
        handler.handleTextMessage(webSocketSession, msg);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(webSocketSession).sendMessage(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("PARSE_ERROR"));
    }

    @Test
    void handleTextMessage_unclosedBrace_triggersParseError() throws Exception {
        stubSession("s1", true, null);
        when(sessionRegistry.getAccountId("s1")).thenReturn(java.util.Optional.of("acc-001"));

        TextMessage msg = new TextMessage("{invalid");
        handler.handleTextMessage(webSocketSession, msg);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(webSocketSession).sendMessage(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("PARSE_ERROR"));
    }

    @Test
    void handleTextMessage_emptyPayload_treatedAsUnknownType() throws Exception {
        stubSession("s1", true, null);
        when(sessionRegistry.getAccountId("s1")).thenReturn(java.util.Optional.of("acc-001"));

        TextMessage msg = new TextMessage("");
        assertDoesNotThrow(() -> handler.handleTextMessage(webSocketSession, msg));
    }

    @Test
    void handleTransportError_cleanup() throws Exception {
        stubSession("s1", true, "token=valid-token");
        stubFamilyAuth();
        when(sessionRegistry.register("acc-001", webSocketSession)).thenReturn(null);
        when(offlineAlertQueue.drain("acc-001")).thenReturn(List.of());
        handler.afterConnectionEstablished(webSocketSession);

        handler.handleTransportError(webSocketSession, new RuntimeException("test error"));

        verify(sessionRegistry).unregister(webSocketSession);
    }

    @Test
    void pushAlert_online() throws Exception {
        when(sessionRegistry.getSession("acc-001")).thenReturn(java.util.Optional.of(webSocketSession));
        when(webSocketSession.isOpen()).thenReturn(true);

        WebSocketPayloads.AlertTriggered alert = WebSocketPayloads.AlertTriggered.of(
                "alert-1", "SOS", "L3", Instant.now(), "trip-1");
        handler.pushAlert("acc-001", alert);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(webSocketSession).sendMessage(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("alert_triggered"));
    }

    @Test
    void pushAlert_offline_enqueuesMessage() throws Exception {
        when(sessionRegistry.getSession("acc-001")).thenReturn(java.util.Optional.empty());

        WebSocketPayloads.AlertTriggered alert = WebSocketPayloads.AlertTriggered.of(
                "alert-1", "SOS", "L3", Instant.now(), "trip-1");
        handler.pushAlert("acc-001", alert);

        verify(offlineAlertQueue).enqueue(eq("acc-001"), anyString());
        verify(webSocketSession, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void pushAccessGranted() throws Exception {
        when(sessionRegistry.getSession("acc-001")).thenReturn(java.util.Optional.of(webSocketSession));
        when(webSocketSession.isOpen()).thenReturn(true);

        WebSocketPayloads.AccessGranted msg = WebSocketPayloads.AccessGranted.of("d-001", "REGULAR_60S");
        handler.pushAccessGranted("acc-001", msg);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(webSocketSession).sendMessage(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("access_granted"));
    }

    @Test
    void pushAccessRevoked() throws Exception {
        when(sessionRegistry.getSession("acc-001")).thenReturn(java.util.Optional.of(webSocketSession));
        when(webSocketSession.isOpen()).thenReturn(true);

        WebSocketPayloads.AccessRevoked msg = WebSocketPayloads.AccessRevoked.of("d-001", "TIMEOUT");
        handler.pushAccessRevoked("acc-001", msg);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(webSocketSession).sendMessage(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("access_revoked"));
    }

    @Test
    void broadcastDriverStatus() throws Exception {
        when(sessionRegistry.getSubscribers("d-001")).thenReturn(Set.of("acc-001", "acc-002"));
        WebSocketSession session2 = mock(WebSocketSession.class);
        when(session2.isOpen()).thenReturn(true);
        when(session2.getId()).thenReturn("s2");
        when(sessionRegistry.getSession("acc-001")).thenReturn(java.util.Optional.of(webSocketSession));
        when(sessionRegistry.getSession("acc-002")).thenReturn(java.util.Optional.of(session2));
        when(webSocketSession.isOpen()).thenReturn(true);

        WebSocketPayloads.DriverStatusSnapshot snapshot = WebSocketPayloads.DriverStatusSnapshot.create(
                "d-001", "v-001", "DRIVING");
        handler.broadcastDriverStatus("d-001", snapshot);

        verify(webSocketSession).sendMessage(any(TextMessage.class));
        verify(session2).sendMessage(any(TextMessage.class));
    }

    @Test
    void broadcastAlertToSubscribers() throws Exception {
        when(sessionRegistry.getSubscribers("d-001")).thenReturn(Set.of("acc-001"));
        when(sessionRegistry.getSession("acc-001")).thenReturn(java.util.Optional.of(webSocketSession));
        when(webSocketSession.isOpen()).thenReturn(true);

        WebSocketPayloads.AlertTriggered alert = WebSocketPayloads.AlertTriggered.of(
                "alert-1", "SOS", "L3", Instant.now(), "trip-1");
        handler.broadcastAlertToSubscribers("d-001", alert);

        verify(webSocketSession).sendMessage(any(TextMessage.class));
    }

    @Test
    void sendMessage_sendFails_gracefullyHandlesIOException() throws Exception {
        stubSession("s1", true, null);
        when(sessionRegistry.getSession("acc-001")).thenReturn(java.util.Optional.of(webSocketSession));
        when(webSocketSession.isOpen()).thenReturn(true);
        doThrow(new IOException("send failed")).when(webSocketSession).sendMessage(any(TextMessage.class));

        WebSocketPayloads.AccessGranted msg = WebSocketPayloads.AccessGranted.of("d-001", "REGULAR_60S");
        assertDoesNotThrow(() -> handler.pushAccessGranted("acc-001", msg));
    }
}
