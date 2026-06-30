package com.aiot.domain.event;

import java.util.function.Consumer;

/**
 * 领域事件总线发布者接口。
 * 定义事件发布和消费方注册的契约。
 */
public interface DomainEventPublisher {

    /**
     * 发布领域事件。
     * 行为语义：
     * - 边缘侧安全攸关事件在进程内同步回调已注册的消费方后返回成功。
     * - 云端侧或跨进程事件写入 outbox 表、与聚合根状态更新在同一事务提交后返回成功。
     *
     * @param event 领域事件
     * @param <E>   事件类型
     */
    <E extends DomainEvent> void publish(E event);

    /**
     * 注册同步消费处理器（边缘侧安全攸关链路）。
     * 注册后，当 publish 被调用且当前运行环境为边缘侧时，在 publish 返回前同步执行 handler。
     *
     * @param eventTypeName 事件类型名称（如 "RiskDeterminedEvent"）
     * @param handler       事件处理器
     * @param <E>           事件类型
     */
    <E extends DomainEvent> void registerSyncHandler(String eventTypeName, Consumer<E> handler);

    /**
     * 注册异步消费处理器（云端侧非实时路径）。
     * 注册后，当事件从 outbox 表投递至消息队列并被消费方拉取时，执行 handler。
     *
     * @param eventTypeName 事件类型名称
     * @param handler       事件处理器
     * @param <E>           事件类型
     * @return 订阅句柄，可通过 cancel() 方法注销
     */
    <E extends DomainEvent> Subscription registerAsyncHandler(String eventTypeName, Consumer<E> handler);

    /**
     * 注销同步消费处理器。
     *
     * @param eventTypeName 事件类型名称
     * @return true 如果成功注销，false 如果处理器不存在
     */
    boolean unregisterSyncHandler(String eventTypeName);

    /**
     * 异步消费订阅句柄。
     */
    interface Subscription {
        /**
         * 取消订阅。
         */
        void cancel();

        /**
         * 检查订阅是否仍然活跃。
         *
         * @return true 如果订阅活跃
         */
        boolean isActive();
    }
}
