package com.aiot.infra.edge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EdgePersistenceServiceTest {

    @TempDir
    Path tempDir;

    private EdgePersistenceService service;
    private EdgeProperties properties;
    private ObjectMapper objectMapper;
    private String dbPath;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties = new EdgeProperties();
        dbPath = tempDir.resolve("test.db").toString();
        properties.setSqlitePath(dbPath);
        properties.setMaxRetries(5);

        service = new EdgePersistenceService(properties, objectMapper);
        service.init();
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Test
    void init_shouldCreateDatabaseAndTables() {
        assertNotNull(service);
        assertEquals(0, service.countPending());
    }

    @Test
    void saveOfflineMessage_shouldStoreMessage() {
        service.saveOfflineMessage("test/topic", "{}", 1, Instant.now().plusSeconds(3600));

        assertEquals(1, service.countPending());
    }

    @Test
    void fetchPendingMessages_shouldReturnMessagesOrderedByCreatedAt() {
        Instant future = Instant.now().plusSeconds(3600);
        service.saveOfflineMessage("topic/1", "first", 1, future);
        service.saveOfflineMessage("topic/2", "second", 1, future);

        List<EdgePersistenceService.OfflineMessage> messages = service.fetchPendingMessages(10);

        assertEquals(2, messages.size());
        assertEquals("first", messages.get(0).payload());
        assertEquals("second", messages.get(1).payload());
    }

    @Test
    void fetchPendingMessages_shouldNotReturnExpiredMessages() {
        Instant past = Instant.now().minusSeconds(3600);
        service.saveOfflineMessage("topic/old", "expired", 1, past);

        List<EdgePersistenceService.OfflineMessage> messages = service.fetchPendingMessages(10);

        assertEquals(0, messages.size());
    }

    @Test
    void markSent_shouldDeleteMessage() {
        Instant future = Instant.now().plusSeconds(3600);
        service.saveOfflineMessage("topic", "payload", 1, future);
        List<EdgePersistenceService.OfflineMessage> messages = service.fetchPendingMessages(1);

        service.markSent(messages.get(0).id());
        assertEquals(0, service.countPending());
    }

    @Test
    void incrementRetry_shouldIncreaseRetryCount() {
        Instant future = Instant.now().plusSeconds(3600);
        service.saveOfflineMessage("topic", "payload", 1, future);
        List<EdgePersistenceService.OfflineMessage> messages = service.fetchPendingMessages(1);
        assertEquals(0, messages.get(0).retryCount());

        service.incrementRetry(messages.get(0).id());

        List<EdgePersistenceService.OfflineMessage> afterRetry = service.fetchPendingMessages(1);
        assertEquals(1, afterRetry.size());
        assertEquals(1, afterRetry.get(0).retryCount());
    }

    @Test
    void incrementRetry_shouldMarkAsFailedWhenRetryExceeded() {
        Instant future = Instant.now().plusSeconds(3600);
        service.saveOfflineMessage("topic", "payload", 1, future);
        List<EdgePersistenceService.OfflineMessage> messages = service.fetchPendingMessages(1);

        for (int i = 0; i < properties.getMaxRetries(); i++) {
            service.incrementRetry(messages.get(0).id());
        }

        assertEquals(0, service.countPending());
    }

    @Test
    void purgeExpired_shouldDeleteExpiredMessages() {
        Instant past = Instant.now().minusSeconds(3600);
        service.saveOfflineMessage("topic/old", "expired1", 1, past);
        service.saveOfflineMessage("topic/old2", "expired2", 1, past);

        int deleted = service.purgeExpired();

        assertTrue(deleted >= 2);
    }

    @Test
    void purgeExpired_shouldNotDeleteValidMessages() {
        Instant future = Instant.now().plusSeconds(3600);
        service.saveOfflineMessage("topic", "valid", 1, future);

        int deleted = service.purgeExpired();

        assertEquals(0, deleted);
        assertEquals(1, service.countPending());
    }

    @Test
    void countPending_shouldReturnOnlyPendingMessages() {
        Instant future = Instant.now().plusSeconds(3600);
        service.saveOfflineMessage("t/1", "p1", 1, future);
        service.saveOfflineMessage("t/2", "p2", 1, future);
        service.saveOfflineMessage("t/3", "p3", 1, future);

        assertEquals(3, service.countPending());
    }

    @Test
    void saveOfflineMessage_withDifferentQos_shouldStore() {
        Instant future = Instant.now().plusSeconds(3600);
        service.saveOfflineMessage("topic/0", "qos0", 0, future);
        service.saveOfflineMessage("topic/1", "qos1", 1, future);
        service.saveOfflineMessage("topic/2", "qos2", 2, future);

        assertEquals(3, service.countPending());
    }

    @Test
    void shutdown_shouldCloseConnection() {
        service.shutdown();
        assertDoesNotThrow(() -> service.shutdown());
    }
}
