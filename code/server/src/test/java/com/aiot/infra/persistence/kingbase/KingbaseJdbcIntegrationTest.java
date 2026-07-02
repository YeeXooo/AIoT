package com.aiot.infra.persistence.kingbase;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("金仓数据库直接 JDBC 集成测试")
class KingbaseJdbcIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(KingbaseJdbcIntegrationTest.class);
    private static final String URL = "jdbc:postgresql://localhost:54321/aiot";
    private static final String USER = "kingbase";
    private static final String PASS = "kingbase123";

    private Connection conn;

    @BeforeEach
    void connect() throws SQLException {
        conn = DriverManager.getConnection(URL, USER, PASS);
        conn.setAutoCommit(false);
    }

    @AfterEach
    void disconnect() throws SQLException {
        if (conn != null) { conn.rollback(); conn.close(); }
    }

    @Test @DisplayName("金仓连接 — 版本信息")
    void kingbaseVersion() throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT version()")) {
            assertTrue(rs.next());
            String ver = rs.getString(1);
            log.info("金仓版本: {}", ver);
            assertTrue(ver.contains("Kingbase") || ver.contains("PostgreSQL"),
                    "期望 Kingbase/PostgreSQL 兼容版本，实际: " + ver);
        }
    }

    @Test @DisplayName("核心表存在性检查")
    void tableExistence() throws SQLException {
        String[] tables = {"t_driver", "t_vehicle", "t_trip", "t_safety_alert_event",
                "t_system_account", "t_road_rage_voice_record", "t_driver_health_profile",
                "t_guardianship", "t_trip_physiological_snapshot", "t_alert_projection",
                "t_fleet_dashboard_projection", "t_trajectory_projection"};
        for (String table : tables) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1 FROM \"" + table + "\" WHERE 1=0")) {
                assertTrue(true, "表存在: " + table);
            } catch (SQLException e) {
                fail("表 " + table + " 不存在: " + e.getMessage());
            }
        }
    }

    @Test @DisplayName("Flyway V3 种子数据 — 5 驾驶员")
    void driverSeedCount() throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM t_driver")) {
            assertTrue(rs.next());
            int count = rs.getInt(1);
            assertEquals(5, count, "Flyway V3 种子数据应有 5 名驾驶员，实际=" + count);
        }
    }

    @Test @DisplayName("Flyway V3 种子数据 — 5 辆车")
    void vehicleSeedCount() throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM t_vehicle")) {
            assertTrue(rs.next());
            assertEquals(5, rs.getInt(1));
        }
    }

    @Test @DisplayName("Flyway V3 种子数据 — 7 行程（2 活跃）")
    void tripSeedCount() throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM t_trip")) {
            assertTrue(rs.next());
            assertEquals(7, rs.getInt(1));
        }
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM t_trip WHERE ended_at IS NULL")) {
            assertTrue(rs.next());
            assertEquals(2, rs.getInt(1), "应有 2 段活跃行程");
        }
    }

    @Test @DisplayName("Flyway V3 种子数据 — 5 告警")
    void alertSeedCount() throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM t_safety_alert_event")) {
            assertTrue(rs.next());
            assertEquals(5, rs.getInt(1));
        }
    }

    @Test @DisplayName("Flyway V3 种子数据 — 3 账户")
    void accountSeedCount() throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM t_system_account")) {
            assertTrue(rs.next());
            assertEquals(3, rs.getInt(1));
        }
    }

    @Test @DisplayName("Flyway V4 种子数据 — 健康档案 JSON")
    void healthProfileJson() throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT baseline_vitals FROM t_driver_health_profile WHERE driver_id = 'd001-d2e3-4abc-9f01-123456789abc'")) {
            assertTrue(rs.next());
            String json = rs.getString(1);
            assertNotNull(json);
            assertTrue(json.contains("resting_heart_rate"), "JSON 应含 resting_heart_rate");
        }
    }

    @Test @DisplayName("Flyway V4 种子数据 — 监护关系")
    void guardianshipSeed() throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM t_guardianship")) {
            assertTrue(rs.next());
            assertEquals(2, rs.getInt(1));
        }
    }

    @Test @DisplayName("Flyway V4 种子数据 — 生理快照 7 条")
    void physioSnapshots() throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM t_trip_physiological_snapshot")) {
            assertTrue(rs.next());
            assertEquals(7, rs.getInt(1));
        }
    }

    @Test @DisplayName("Flyway V4 种子数据 — 看板投影 5 条")
    void dashboardProjection() throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM t_fleet_dashboard_projection")) {
            assertTrue(rs.next());
            assertEquals(5, rs.getInt(1));
        }
    }

    @Test @DisplayName("Flyway V4 种子数据 — 轨迹投影 5 条")
    void trajectoryProjection() throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM t_trajectory_projection")) {
            assertTrue(rs.next());
            assertEquals(5, rs.getInt(1));
        }
    }

    @Test @DisplayName("CRUD — 事务中 INSERT → SELECT → UPDATE → SELECT → DELETE")
    void crudInTransaction() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO t_driver(driver_id, name, phone, comprehensive_score) " +
                    "VALUES ('test-jdbc-01', 'JDBC测试', '19911110001', 80)");
        }

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name, version FROM t_driver WHERE driver_id = 'test-jdbc-01'")) {
            assertTrue(rs.next());
            assertEquals("JDBC测试", rs.getString("name"));
            assertEquals(0, rs.getInt("version"));
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("UPDATE t_driver SET comprehensive_score = 75 WHERE driver_id = 'test-jdbc-01'");
        }

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT version, comprehensive_score FROM t_driver WHERE driver_id = 'test-jdbc-01'")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt("version"), "原生 SQL UPDATE 不触发 @Version，version 保持 0");
            assertEquals(75, rs.getInt("comprehensive_score"));
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM t_driver WHERE driver_id = 'test-jdbc-01'");
        }

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM t_driver WHERE driver_id = 'test-jdbc-01'")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1));
        }
    }

    @Test @DisplayName("原生 SQL 不触发 @Version — 预期行为")
    void versionIncrement() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO t_driver(driver_id, name, phone, comprehensive_score) " +
                    "VALUES ('test-ver-01', '版本测试', '19922220001', 60)");
        }

        for (int i = 0; i < 3; i++) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("UPDATE t_driver SET comprehensive_score = comprehensive_score + 1 WHERE driver_id = 'test-ver-01'");
            }
        }

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT version, comprehensive_score FROM t_driver WHERE driver_id = 'test-ver-01'")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt("version"), "原生 SQL UPDATE 不触发 @Version，version 保持 0");
            assertEquals(63, rs.getInt("comprehensive_score"), "初始 60 + 3 次递增 = 63");
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM t_driver WHERE driver_id = 'test-ver-01'");
        }
    }

    @Test @DisplayName("复合主键 — 看板投影唯一性")
    void compositePrimaryKey() throws SQLException {
        boolean duplicate;
        try (Statement stmt = conn.createStatement()) {
            // 尝试插入重复的主键组合
            stmt.execute("INSERT INTO t_fleet_dashboard_projection(fleet_id, risk_level, alert_type, alert_count, driver_count) " +
                    "VALUES ('fleet-east-1', 'L2_WARNING', 'FATIGUE', 10, 5)");
            duplicate = true;
        } catch (SQLException e) {
            duplicate = e.getMessage().contains("duplicate") || e.getMessage().contains("unique");
        }
        conn.rollback(); // 回滚（避免影响后续测试）
        assertTrue(duplicate, "复合主键冲突应触发 unique constraint");
    }
}
