package com.aiot.infra.edge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 边缘侧 SQLite 本地持久化服务。
 * <p>
 * 在边缘设备上提供轻量级本地存储，用于离线缓冲消息、
 * 事件记录和传感器快照。初始化时自动建表，支持按时间范围
 * 和批量大小分页查询。
 * </p>
 * <p>
 * 设计依据：docs/ood_infrastructure.md §3.7.4、§3.8
 * </p>
 */
public class EdgePersistenceService {

    private static final Logger log = LoggerFactory.getLogger(EdgePersistenceService.class);

    private final EdgeProperties properties;
    private final ObjectMapper objectMapper;
    private final String dbPath;
    private Connection connection;

    public EdgePersistenceService(EdgeProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.dbPath = properties.getSqlitePath();
    }

    /**
     * 初始化数据库，建表。
     */
    public synchronized void init() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            createTables();
            log.info("SQLite 本地数据库已初始化: path={}", dbPath);
        } catch (Exception e) {
            log.error("SQLite 初始化失败: path={}", dbPath, e);
            throw new RuntimeException("SQLite initialization failed", e);
        }
    }

    /**
     * 保存一条消息到离线缓冲表。
     *
     * @param topic      MQTT 主题
     * @param payload    消息负载（JSON 字符串）
     * @param qos        QoS 等级
     * @param expiredAt  过期时间
     */
    public void saveOfflineMessage(String topic, String payload, int qos, Instant expiredAt) {
        String sql = "INSERT INTO offline_buffer (id, topic, payload, qos, created_at, expired_at, retry_count, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, 0, 'PENDING')";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, UUID.randomUUID().toString());
            stmt.setString(2, topic);
            stmt.setString(3, payload);
            stmt.setInt(4, qos);
            stmt.setString(5, Instant.now().toString());
            stmt.setString(6, expiredAt.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("离线消息保存失败: topic={}", topic, e);
        }
    }

    /**
     * 分页查询待发送的离线消息。
     *
     * @param limit 每次查询条数
     * @return 待发送的消息列表
     */
    public List<OfflineMessage> fetchPendingMessages(int limit) {
        List<OfflineMessage> results = new ArrayList<>();
        String sql = "SELECT id, topic, payload, qos, retry_count FROM offline_buffer "
                + "WHERE status = 'PENDING' AND expired_at > ? "
                + "ORDER BY created_at ASC LIMIT ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, Instant.now().toString());
            stmt.setInt(2, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(new OfflineMessage(
                            rs.getString("id"),
                            rs.getString("topic"),
                            rs.getString("payload"),
                            rs.getInt("qos"),
                            rs.getInt("retry_count")
                    ));
                }
            }
        } catch (SQLException e) {
            log.error("查询待发送消息失败", e);
        }
        return results;
    }

    /**
     * 标记消息为已发送（删除记录）。
     */
    public void markSent(String messageId) {
        String sql = "DELETE FROM offline_buffer WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, messageId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("标记消息已发送失败: id={}", messageId, e);
        }
    }

    /**
     * 增加消息重试次数，超过上限则标记为 FAILED。
     */
    public void incrementRetry(String messageId) {
        String sql = "UPDATE offline_buffer SET retry_count = retry_count + 1, "
                + "status = CASE WHEN retry_count + 1 >= ? THEN 'FAILED' ELSE 'PENDING' END "
                + "WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, properties.getMaxRetries());
            stmt.setString(2, messageId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("重试计数更新失败: id={}", messageId, e);
        }
    }

    /**
     * 清除过期消息（保留时间外的）。
     */
    public int purgeExpired() {
        String sql = "DELETE FROM offline_buffer WHERE expired_at <= ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, Instant.now().toString());
            int deleted = stmt.executeUpdate();
            if (deleted > 0) {
                log.info("清除过期消息: {} 条", deleted);
            }
            return deleted;
        } catch (SQLException e) {
            log.error("清除过期消息失败", e);
            return 0;
        }
    }

    /**
     * 获取当前缓冲消息数。
     */
    public int countPending() {
        String sql = "SELECT COUNT(*) FROM offline_buffer WHERE status = 'PENDING'";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.getInt(1);
        } catch (SQLException e) {
            log.error("计数待发送消息失败", e);
            return 0;
        }
    }

    /**
     * 关闭数据库连接。
     */
    public synchronized void shutdown() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                log.info("SQLite 连接已关闭");
            }
        } catch (SQLException e) {
            log.error("SQLite 关闭异常", e);
        }
    }

    // ── 建表 ──

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS offline_buffer (
                        id TEXT PRIMARY KEY,
                        topic TEXT NOT NULL,
                        payload TEXT NOT NULL,
                        qos INTEGER NOT NULL DEFAULT 1,
                        created_at TEXT NOT NULL,
                        expired_at TEXT NOT NULL,
                        retry_count INTEGER NOT NULL DEFAULT 0,
                        status TEXT NOT NULL DEFAULT 'PENDING'
                    )
                    """);

            // 索引：加速按状态和创建时间查询
            stmt.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_offline_buffer_status
                    ON offline_buffer(status, created_at)
                    """);

            stmt.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_offline_buffer_expired
                    ON offline_buffer(expired_at)
                    """);
        }
    }

    // ── 内部类型 ──

    public record OfflineMessage(
            String id,
            String topic,
            String payload,
            int qos,
            int retryCount
    ) {}
}
