package com.aiot.infra.adapter;

import com.aiot.domain.port.NotificationPort;
import com.aiot.domain.shared.AccountId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 通知推送适配器（打桩实现）。
 * <p>
 * 记录推送日志，不实际调用 SMN / Push Kit。
 * </p>
 * <p>
 * 设计依据：docs/ood_infrastructure.md §3.4.6
 * </p>
 */
@Component
public class NotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(NotificationAdapter.class);

    @Override
    public void pushNotification(AccountId recipient, NotificationPayload payload) throws NotificationException {
        log.info("Notification pushed: recipient={}, type={}, title={}, priority={}",
                recipient.id(),
                payload.type(),
                payload.title(),
                payload.priority());

        log.debug("Notification body: {}", payload.body());

        // 打桩实现，不实际推送
    }
}
