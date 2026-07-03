package com.aiot.interfaces.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSocketSessionRegistryTest {

    private WebSocketSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new WebSocketSessionRegistry();
    }

    private WebSocketSession mockSession(String sessionId, boolean open) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.isOpen()).thenReturn(open);
        return session;
    }

    @Test
    void register_newAccount_returnsNull() {
        WebSocketSession session = mockSession("s1", true);
        assertNull(registry.register("acc1", session));
    }

    @Test
    void register_existingAccount_replacesAndReturnsOld() {
        WebSocketSession oldSession = mockSession("s1", true);
        WebSocketSession newSession = mockSession("s2", true);
        registry.register("acc1", oldSession);
        WebSocketSession returned = registry.register("acc1", newSession);
        assertSame(oldSession, returned);
        assertSame(newSession, registry.getSession("acc1").orElse(null));
    }

    @Test
    void register_existingAccount_oldSessionClosed_keepsReverseMappingForOld() {
        WebSocketSession oldSession = mockSession("s1", false);
        WebSocketSession newSession = mockSession("s2", true);
        registry.register("acc1", oldSession);
        registry.register("acc1", newSession);
        assertTrue(registry.getAccountId("s1").isPresent());
    }

    @Test
    void register_oldSessionOpen_removesReverseMapping() {
        WebSocketSession oldSession = mockSession("s1", true);
        WebSocketSession newSession = mockSession("s2", true);
        registry.register("acc1", oldSession);
        registry.register("acc1", newSession);
        assertFalse(registry.getAccountId("s1").isPresent());
    }

    @Test
    void unregister_existingSession_removesMappings() {
        WebSocketSession session = mockSession("s1", true);
        registry.register("acc1", session);
        registry.unregister(session);
        assertFalse(registry.getSession("acc1").isPresent());
        assertFalse(registry.getAccountId("s1").isPresent());
    }

    @Test
    void unregister_nonExistentSession_doesNothing() {
        WebSocketSession session = mockSession("s1", true);
        assertDoesNotThrow(() -> registry.unregister(session));
    }

    @Test
    void unregister_removesAccountFromSubscriptions() {
        WebSocketSession session = mockSession("s1", true);
        registry.register("acc1", session);
        registry.subscribe("driver1", "acc1", 3);
        registry.unregister(session);
        assertTrue(registry.getSubscribers("driver1").isEmpty());
    }

    @Test
    void subscribe_success_returnsTrue() {
        WebSocketSession session = mockSession("s1", true);
        registry.register("acc1", session);
        assertTrue(registry.subscribe("driver1", "acc1", 3));
    }

    @Test
    void subscribe_exceedsMax_returnsFalse() {
        WebSocketSession s1 = mockSession("s1", true);
        WebSocketSession s2 = mockSession("s2", true);
        WebSocketSession s3 = mockSession("s3", true);
        WebSocketSession s4 = mockSession("s4", true);
        registry.register("acc1", s1);
        registry.register("acc2", s2);
        registry.register("acc3", s3);
        registry.register("acc4", s4);
        assertTrue(registry.subscribe("driver1", "acc1", 3));
        assertTrue(registry.subscribe("driver1", "acc2", 3));
        assertTrue(registry.subscribe("driver1", "acc3", 3));
        assertFalse(registry.subscribe("driver1", "acc4", 3));
    }

    @Test
    void subscribe_alreadySubscribed_noDoubleLimit() {
        WebSocketSession s1 = mockSession("s1", true);
        WebSocketSession s2 = mockSession("s2", true);
        WebSocketSession s3 = mockSession("s3", true);
        registry.register("acc1", s1);
        registry.register("acc2", s2);
        registry.register("acc3", s3);
        registry.subscribe("driver1", "acc1", 2);
        registry.subscribe("driver1", "acc2", 2);
        assertTrue(registry.subscribe("driver1", "acc1", 2));
        assertFalse(registry.subscribe("driver1", "acc3", 2));
    }

    @Test
    void unsubscribe_existingSubscription_removesIt() {
        WebSocketSession session = mockSession("s1", true);
        registry.register("acc1", session);
        registry.subscribe("driver1", "acc1", 3);
        registry.unsubscribe("driver1", "acc1");
        assertTrue(registry.getSubscribers("driver1").isEmpty());
    }

    @Test
    void unsubscribe_nonExistentDriver_doesNothing() {
        assertDoesNotThrow(() -> registry.unsubscribe("driver1", "acc1"));
    }

    @Test
    void getSession_existing_returnsSession() {
        WebSocketSession session = mockSession("s1", true);
        registry.register("acc1", session);
        assertTrue(registry.getSession("acc1").isPresent());
        assertSame(session, registry.getSession("acc1").get());
    }

    @Test
    void getSession_nonExistent_returnsEmpty() {
        assertFalse(registry.getSession("nobody").isPresent());
    }

    @Test
    void getAccountId_existing_returnsId() {
        WebSocketSession session = mockSession("s1", true);
        registry.register("acc1", session);
        assertEquals("acc1", registry.getAccountId("s1").orElse(null));
    }

    @Test
    void getAccountId_nonExistent_returnsEmpty() {
        assertFalse(registry.getAccountId("s1").isPresent());
    }

    @Test
    void getSubscribers_existing_returnsCopy() {
        WebSocketSession s1 = mockSession("s1", true);
        WebSocketSession s2 = mockSession("s2", true);
        registry.register("acc1", s1);
        registry.register("acc2", s2);
        registry.subscribe("driver1", "acc1", 3);
        registry.subscribe("driver1", "acc2", 3);
        Set<String> subs = registry.getSubscribers("driver1");
        assertEquals(2, subs.size());
        assertTrue(subs.contains("acc1"));
        assertTrue(subs.contains("acc2"));
        subs.add("acc3");
        assertEquals(2, registry.getSubscribers("driver1").size());
    }

    @Test
    void getSubscribers_nonExistent_returnsEmpty() {
        assertTrue(registry.getSubscribers("driver1").isEmpty());
    }

    @Test
    void getConnectedAccounts_returnsAllAccounts() {
        WebSocketSession s1 = mockSession("s1", true);
        WebSocketSession s2 = mockSession("s2", true);
        registry.register("acc1", s1);
        registry.register("acc2", s2);
        Set<String> accounts = registry.getConnectedAccounts();
        assertEquals(2, accounts.size());
    }

    @Test
    void connectionCount_tracksRegisterAndUnregister() {
        assertEquals(0, registry.connectionCount());
        WebSocketSession s1 = mockSession("s1", true);
        WebSocketSession s2 = mockSession("s2", true);
        registry.register("acc1", s1);
        assertEquals(1, registry.connectionCount());
        registry.register("acc2", s2);
        assertEquals(2, registry.connectionCount());
        registry.unregister(s1);
        assertEquals(1, registry.connectionCount());
    }

    @Test
    void connectionCount_replacingExistingDoesNotIncrement() {
        WebSocketSession s1 = mockSession("s1", true);
        WebSocketSession s2 = mockSession("s2", true);
        registry.register("acc1", s1);
        registry.register("acc1", s2);
        assertEquals(1, registry.connectionCount());
    }

    @Test
    void concurrentRegisterAndUnregister() throws Exception {
        int threadCount = 10;
        int accountsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                for (int i = 0; i < accountsPerThread; i++) {
                    String accountId = "acc-" + threadId + "-" + i;
                    WebSocketSession session = mockSession("s-" + threadId + "-" + i, true);
                    registry.register(accountId, session);
                }
                latch.countDown();
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        assertEquals(threadCount * accountsPerThread, registry.connectionCount());
        executor.shutdown();
    }

    @Test
    void concurrentSubscribe() throws Exception {
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int t = 0; t < threadCount; t++) {
            String accountId = "acc" + t;
            WebSocketSession session = mockSession("s" + t, true);
            registry.register(accountId, session);
        }

        CountDownLatch latch = new CountDownLatch(threadCount);
        for (int t = 0; t < threadCount; t++) {
            final String accountId = "acc" + t;
            executor.submit(() -> {
                registry.subscribe("driver1", accountId, 10);
                latch.countDown();
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        assertEquals(threadCount, registry.getSubscribers("driver1").size());
        executor.shutdown();
    }

    @Test
    void subscribeWithMaxZero_alwaysRejects() {
        WebSocketSession session = mockSession("s1", true);
        registry.register("acc1", session);
        assertFalse(registry.subscribe("driver1", "acc1", 0));
    }

}
