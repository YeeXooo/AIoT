package com.aiot.infra.adapter;

import com.aiot.domain.port.NotificationPort.NotificationException;
import com.aiot.domain.port.NotificationPort.NotificationPayload;
import com.aiot.domain.port.NotificationPort.NotificationPriority;
import com.aiot.domain.port.NotificationPort.NotificationType;
import com.aiot.domain.shared.AccountId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NotificationAdapterTest {

    private NotificationAdapter adapter;
    private AccountId recipient;

    @BeforeEach
    void setUp() {
        adapter = new NotificationAdapter();
        recipient = new AccountId("acc-1");
    }

    @Test
    void pushNotification_shouldNotThrowForValidPayload() {
        NotificationPayload payload = new NotificationPayload(
                NotificationType.ALERT, "Alert Title", "Alert body content",
                NotificationPriority.HIGH);

        assertDoesNotThrow(() -> adapter.pushNotification(recipient, payload));
    }

    @Test
    void pushNotification_withUrgentPriority_shouldNotThrow() {
        NotificationPayload payload = new NotificationPayload(
                NotificationType.RESCUE_REPORT, "SOS Alert", "Emergency rescue triggered",
                NotificationPriority.URGENT);

        assertDoesNotThrow(() -> adapter.pushNotification(recipient, payload));
    }

    @Test
    void pushNotification_withLowPriority_shouldNotThrow() {
        NotificationPayload payload = new NotificationPayload(
                NotificationType.STATUS_SNAPSHOT, "Status Update", "All systems normal",
                NotificationPriority.LOW);

        assertDoesNotThrow(() -> adapter.pushNotification(recipient, payload));
    }

    @Test
    void pushNotification_withPerformanceType_shouldNotThrow() {
        NotificationPayload payload = new NotificationPayload(
                NotificationType.PERFORMANCE, "Performance Warning", "Score below threshold",
                NotificationPriority.NORMAL);

        assertDoesNotThrow(() -> adapter.pushNotification(recipient, payload));
    }

    @Test
    void pushNotification_withNullBody_shouldNotThrow() {
        NotificationPayload payload = new NotificationPayload(
                NotificationType.ALERT, "Title", null,
                NotificationPriority.HIGH);

        assertDoesNotThrow(() -> adapter.pushNotification(recipient, payload));
    }

    @Test
    void pushNotification_withEmptyTitle_shouldNotThrow() {
        NotificationPayload payload = new NotificationPayload(
                NotificationType.ALERT, "", "Body",
                NotificationPriority.HIGH);

        assertDoesNotThrow(() -> adapter.pushNotification(recipient, payload));
    }
}
