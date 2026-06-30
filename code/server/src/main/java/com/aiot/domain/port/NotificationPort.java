package com.aiot.domain.port;

import com.aiot.domain.shared.AccountId;

/**
 * 通知推送端口。
 * <p>
 * 负责向外部推送通知（告警、救援报告、状态快照、绩效预警）。
 * 服务于 DS-12、DS-16、DS-09 等所有需要向外部推送通知的场景。
 * </p>
 * <p>
 * 设计依据：docs/ood_domain.md 决策 19 端口 1
 * </p>
 */
public interface NotificationPort {

    /**
     * 推送通知。
     *
     * @param recipient 接收方账户标识
     * @param payload   通知内容
     * @throws NotificationException 推送异常
     */
    void pushNotification(AccountId recipient, NotificationPayload payload) throws NotificationException;

    /**
     * 通知内容。
     *
     * @param type     通知类型
     * @param title    标题
     * @param body     正文
     * @param priority 优先级
     */
    record NotificationPayload(
            NotificationType type,
            String title,
            String body,
            NotificationPriority priority
    ) { }

    /**
     * 通知类型枚举。
     */
    enum NotificationType {
        ALERT,           // 告警
        RESCUE_REPORT,   // 救援报告
        STATUS_SNAPSHOT, // 状态快照
        PERFORMANCE      // 绩效预警
    }

    /**
     * 通知优先级枚举。
     */
    enum NotificationPriority {
        LOW,
        NORMAL,
        HIGH,
        URGENT
    }

    /**
     * 通知推送异常。
     */
    sealed class NotificationException extends Exception permits
            NotificationException.DeliveryFailedException,
            NotificationException.RecipientUnreachableException {

        public NotificationException(String message) {
            super(message);
        }

        /**
         * 投递失败。
         */
        public static final class DeliveryFailedException extends NotificationException {
            public DeliveryFailedException(String message) {
                super(message);
            }
        }

        /**
         * 接收方不可达。
         */
        public static final class RecipientUnreachableException extends NotificationException {
            public RecipientUnreachableException(String message) {
                super(message);
            }
        }
    }
}
