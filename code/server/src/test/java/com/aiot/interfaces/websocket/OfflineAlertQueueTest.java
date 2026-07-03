package com.aiot.interfaces.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class OfflineAlertQueueTest {

    private static final int MAX_ALERTS = 5;

    private OfflineAlertQueue queue;
    private WebSocketProperties properties;

    @BeforeEach
    void setUp() {
        properties = new WebSocketProperties();
        properties.setMaxOfflineAlertsPerReconnect(MAX_ALERTS);
        properties.setOfflineMessageRetentionDays(7);
        queue = new OfflineAlertQueue(properties);
    }

    @Test
    void enqueue_singleMessage_storedCorrectly() {
        queue.enqueue("acc1", "alert-1");
        assertEquals(1, queue.pendingCount("acc1"));
    }

    @Test
    void enqueue_multipleMessages_keepsOrder() {
        queue.enqueue("acc1", "alert-1");
        queue.enqueue("acc1", "alert-2");
        queue.enqueue("acc1", "alert-3");
        List<String> drained = queue.drain("acc1");
        assertEquals(3, drained.size());
        assertEquals("alert-1", drained.get(0));
        assertEquals("alert-2", drained.get(1));
        assertEquals("alert-3", drained.get(2));
    }

    @Test
    void enqueue_exceedsMax_dropsOldest() {
        for (int i = 0; i < MAX_ALERTS + 3; i++) {
            queue.enqueue("acc1", "alert-" + i);
        }
        List<String> drained = queue.drain("acc1");
        assertEquals(MAX_ALERTS, drained.size());
        assertEquals("alert-3", drained.get(0));
        assertEquals("alert-" + (MAX_ALERTS + 2), drained.get(MAX_ALERTS - 1));
    }

    @Test
    void drain_removesQueue() {
        queue.enqueue("acc1", "alert-1");
        queue.enqueue("acc1", "alert-2");
        List<String> drained = queue.drain("acc1");
        assertEquals(2, drained.size());
        assertEquals(0, queue.pendingCount("acc1"));
        assertTrue(queue.drain("acc1").isEmpty());
    }

    @Test
    void drain_nonExistentAccount_returnsEmpty() {
        assertTrue(queue.drain("nobody").isEmpty());
    }

    @Test
    void drain_dropsExpiredMessages() throws Exception {
        WebSocketProperties shortRetention = new WebSocketProperties();
        shortRetention.setMaxOfflineAlertsPerReconnect(MAX_ALERTS);
        shortRetention.setOfflineMessageRetentionDays(0);
        OfflineAlertQueue shortQueue = new OfflineAlertQueue(shortRetention);
        shortQueue.enqueue("acc1", "alert-1");
        Thread.sleep(1);
        List<String> drained = shortQueue.drain("acc1");
        assertTrue(drained.isEmpty());
    }

    @Test
    void pendingCount_nonExistentAccount_returnsZero() {
        assertEquals(0, queue.pendingCount("nobody"));
    }

    @Test
    void totalPendingAccounts_tracksEnqueueAndDrain() {
        assertEquals(0, queue.totalPendingAccounts());
        queue.enqueue("acc1", "alert-1");
        assertEquals(1, queue.totalPendingAccounts());
        queue.enqueue("acc2", "alert-2");
        assertEquals(2, queue.totalPendingAccounts());
        queue.drain("acc1");
        assertEquals(1, queue.totalPendingAccounts());
    }

    @Test
    void enqueue_differentAccounts_independentQueues() {
        queue.enqueue("acc1", "a1-alert");
        queue.enqueue("acc2", "a2-alert");
        assertEquals(1, queue.pendingCount("acc1"));
        assertEquals(1, queue.pendingCount("acc2"));
        queue.drain("acc1");
        assertEquals(0, queue.pendingCount("acc1"));
        assertEquals(1, queue.pendingCount("acc2"));
    }

    @Test
    void concurrentEnqueue() throws Exception {
        int threadCount = 4;
        int alertsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                for (int i = 0; i < alertsPerThread; i++) {
                    queue.enqueue("acc1", "t" + threadId + "-" + i);
                }
                latch.countDown();
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        List<String> drained = queue.drain("acc1");
        assertEquals(MAX_ALERTS, drained.size());
        executor.shutdown();
    }

    @Test
    void concurrentDrainAndEnqueue() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);

        for (int i = 0; i < 10; i++) {
            queue.enqueue("acc1", "pre-" + i);
        }

        executor.submit(() -> {
            for (int i = 0; i < 20; i++) {
                queue.enqueue("acc1", "during-" + i);
            }
            latch.countDown();
        });

        executor.submit(() -> {
            queue.drain("acc1");
            latch.countDown();
        });

        latch.await(5, TimeUnit.SECONDS);
        assertDoesNotThrow(() -> queue.drain("acc1"));
        executor.shutdown();
    }

    @Test
    void enqueue_maxOfflineAlertsOne_storesOnlyLatest() {
        WebSocketProperties oneProps = new WebSocketProperties();
        oneProps.setMaxOfflineAlertsPerReconnect(1);
        oneProps.setOfflineMessageRetentionDays(7);
        OfflineAlertQueue oneQueue = new OfflineAlertQueue(oneProps);
        oneQueue.enqueue("acc1", "first");
        oneQueue.enqueue("acc1", "second");
        List<String> drained = oneQueue.drain("acc1");
        assertEquals(1, drained.size());
        assertEquals("second", drained.get(0));
    }
}
