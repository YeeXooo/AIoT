package com.aiot.infra.edge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EdgeCloudSyncServiceTest {

    @Mock
    private EdgePersistenceService persistence;

    @Mock
    private EdgeMqttClient mqttClient;

    @Mock
    private EdgeProperties properties;

    private EdgeCloudSyncService syncService;

    @BeforeEach
    void setUp() {
        lenient().when(properties.getRetryIntervalSec()).thenReturn(30);
        lenient().when(properties.getBatchSize()).thenReturn(100);
        lenient().when(properties.getBufferRetentionHours()).thenReturn(24);
        lenient().when(properties.getBufferMaxEntries()).thenReturn(10000);
        lenient().when(properties.getDeviceId()).thenReturn("edge-dev-1");
        lenient().when(properties.getMode()).thenReturn("edge");

        ObjectMapper objectMapper = new ObjectMapper();
        syncService = new EdgeCloudSyncService(persistence, mqttClient, properties, objectMapper);
    }

    @Test
    void start_shouldInitializePersistenceAndStartMqtt() {
        syncService.start();

        verify(persistence).init();
        verify(mqttClient).start();
        verify(mqttClient).onConnectionStateChange(any());
    }

    @Test
    void start_shouldNotInitializeTwice() {
        syncService.start();
        syncService.start();

        verify(persistence, times(1)).init();
        verify(mqttClient, times(1)).start();
    }

    @Test
    void stop_shouldShutdownAllComponents() {
        syncService.start();
        syncService.stop();

        verify(mqttClient).stop();
        verify(persistence).shutdown();
    }

    @Test
    void sendWithBuffer_whenConnected_shouldPublishDirectly() {
        when(mqttClient.isConnected()).thenReturn(true);
        when(mqttClient.publish(anyString(), anyString(), anyInt())).thenReturn(true);

        syncService.sendWithBuffer("test/topic", "{}", 1);

        verify(mqttClient).publish("test/topic", "{}", 1);
        verify(persistence, never()).saveOfflineMessage(anyString(), anyString(), anyInt(), any());
    }

    @Test
    void sendWithBuffer_whenNotConnected_shouldSaveToBuffer() {
        when(mqttClient.isConnected()).thenReturn(false);

        syncService.sendWithBuffer("test/topic", "{}", 1);

        verify(persistence).saveOfflineMessage(eq("test/topic"), eq("{}"), eq(1), any());
    }

    @Test
    void sendWithBuffer_whenPublishFails_shouldSaveToBuffer() {
        when(mqttClient.isConnected()).thenReturn(true);
        when(mqttClient.publish(anyString(), anyString(), anyInt())).thenReturn(false);

        syncService.sendWithBuffer("test/topic", "{}", 1);

        verify(mqttClient).publish("test/topic", "{}", 1);
        verify(persistence).saveOfflineMessage(eq("test/topic"), eq("{}"), eq(1), any());
    }

    @Test
    void sendWithBuffer_shouldDeduplicateIdenticalMessages() {
        when(mqttClient.isConnected()).thenReturn(true);
        when(mqttClient.publish(anyString(), anyString(), anyInt())).thenReturn(true);

        syncService.sendWithBuffer("dup/topic", "same-payload", 1);
        syncService.sendWithBuffer("dup/topic", "same-payload", 1);

        verify(mqttClient, times(1)).publish("dup/topic", "same-payload", 1);
    }

    @Test
    void sendWithBuffer_differentPayloads_shouldNotDeduplicate() {
        when(mqttClient.isConnected()).thenReturn(true);
        when(mqttClient.publish(anyString(), anyString(), anyInt())).thenReturn(true);

        syncService.sendWithBuffer("topic", "payload-1", 1);
        syncService.sendWithBuffer("topic", "payload-2", 1);

        verify(mqttClient, times(2)).publish(eq("topic"), anyString(), eq(1));
    }

    @Test
    void pendingCount_shouldDelegateToPersistence() {
        when(persistence.countPending()).thenReturn(42);

        int count = syncService.pendingCount();

        assertEquals(42, count);
    }

    @Test
    void sendWithBuffer_whenBufferFull_shouldPurgeExpired() {
        when(mqttClient.isConnected()).thenReturn(false);
        when(persistence.countPending()).thenReturn(10000, 10000);
        when(properties.getBufferMaxEntries()).thenReturn(10000);

        syncService.sendWithBuffer("topic", "payload", 1);

        verify(persistence).purgeExpired();
    }
}
