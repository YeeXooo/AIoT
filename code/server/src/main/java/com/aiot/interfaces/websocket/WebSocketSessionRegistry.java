package com.aiot.interfaces.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket 会话注册表。
 * <p>
 * 线程安全地管理家属 APP 和车队大屏的 WebSocket 连接映射：
 * <ul>
 *   <li>account → session（多对一：一个账户最多一个连接）</li>
 *   <li>driver → subscribed accounts（一对多：一个驾驶员最多 3 个家属订阅）</li>
 * </ul>
 * </p>
 */
@Component
public class WebSocketSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionRegistry.class);

    /** accountId → WebSocketSession */
    private final ConcurrentHashMap<String, WebSocketSession> accountSessions = new ConcurrentHashMap<>();

    /** driverId → 已订阅的 accountId 集合 */
    private final ConcurrentHashMap<String, Set<String>> driverSubscriptions = new ConcurrentHashMap<>();

    /** sessionId → accountId 反向映射 */
    private final ConcurrentHashMap<String, String> sessionToAccount = new ConcurrentHashMap<>();

    /**
     * 注册新连接。若该账户已有连接，关闭旧连接并替换。
     *
     * @param accountId 账户标识
     * @param session   WebSocket 会话
     * @return 被替换的旧会话（如有），null 表示全新连接
     */
    public WebSocketSession register(String accountId, WebSocketSession session) {
        sessionToAccount.put(session.getId(), accountId);
        WebSocketSession old = accountSessions.put(accountId, session);
        if (old != null && old.isOpen()) {
            log.info("WebSocket 重复连接，关闭旧会话: accountId={}, oldSessionId={}", accountId, old.getId());
            sessionToAccount.remove(old.getId());
        }
        log.info("WebSocket 连接注册: accountId={}, sessionId={}", accountId, session.getId());
        return old;
    }

    /**
     * 移除连接（主动断开或心跳超时）。
     */
    public void unregister(WebSocketSession session) {
        String accountId = sessionToAccount.remove(session.getId());
        if (accountId != null) {
            accountSessions.remove(accountId, session);
            // 清理该账户的所有订阅
            for (Set<String> subscribers : driverSubscriptions.values()) {
                subscribers.remove(accountId);
            }
            log.info("WebSocket 连接注销: accountId={}, sessionId={}", accountId, session.getId());
        }
    }

    /**
     * 订阅驾驶员状态。
     *
     * @param driverId   驾驶员标识
     * @param accountId  家属账户标识
     * @param maxPerDriver 每驾驶员最大订阅数
     * @return 订阅结果：{@code true} 成功，{@code false} 超限
     */
    public boolean subscribe(String driverId, String accountId, int maxPerDriver) {
        Set<String> subscribers = driverSubscriptions.computeIfAbsent(driverId, k -> new CopyOnWriteArraySet<>());
        if (subscribers.size() >= maxPerDriver && !subscribers.contains(accountId)) {
            log.warn("驾驶员订阅数超限: driverId={}, max={}", driverId, maxPerDriver);
            return false;
        }
        subscribers.add(accountId);
        log.info("家属订阅驾驶员状态: driverId={}, accountId={}", driverId, accountId);
        return true;
    }

    /**
     * 取消订阅。
     */
    public void unsubscribe(String driverId, String accountId) {
        Set<String> subscribers = driverSubscriptions.get(driverId);
        if (subscribers != null) {
            subscribers.remove(accountId);
            log.info("家属取消订阅: driverId={}, accountId={}", driverId, accountId);
        }
    }

    /**
     * 获取账户对应的 WebSocket 会话。
     */
    public Optional<WebSocketSession> getSession(String accountId) {
        return Optional.ofNullable(accountSessions.get(accountId));
    }

    /**
     * 通过 sessionId 获取 accountId。
     */
    public Optional<String> getAccountId(String sessionId) {
        return Optional.ofNullable(sessionToAccount.get(sessionId));
    }

    /**
     * 获取订阅某驾驶员的所有账户。
     */
    public Set<String> getSubscribers(String driverId) {
        Set<String> subscribers = driverSubscriptions.get(driverId);
        return subscribers != null ? new HashSet<>(subscribers) : Collections.emptySet();
    }

    /**
     * 获取所有已连接账户。
     */
    public Set<String> getConnectedAccounts() {
        return new HashSet<>(accountSessions.keySet());
    }

    /**
     * 当前连接数。
     */
    public int connectionCount() {
        return accountSessions.size();
    }
}
