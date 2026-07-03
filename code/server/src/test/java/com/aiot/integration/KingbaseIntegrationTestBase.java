package com.aiot.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.properties.hibernate.criteria.literal_handling_mode=BIND",
        "spring.flyway.baseline-on-migrate=true"
})
public abstract class KingbaseIntegrationTestBase {

    protected static final String TEST_PREFIX = "itest-";

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected PlatformTransactionManager txManager;

    @BeforeAll
    void cleanupLeftoverTestData() {
        cleanupByPrefix(TEST_PREFIX);
    }

    @AfterEach
    void cleanupTestData() {
        cleanupByPrefix(TEST_PREFIX);
    }

    protected TransactionTemplate newTransaction() {
        return new TransactionTemplate(txManager);
    }

    private void cleanupByPrefix(String prefix) {
        // 子表 (FK) 先删，主表后删
        safeDelete("t_trip_physiological_snapshot", "trip_id", prefix);
        safeDelete("t_road_rage_voice_record", "record_id", prefix);
        safeDelete("t_safety_alert_event", "alert_id", prefix);
        safeDelete("t_alert_projection", "alert_id", prefix);
        safeDelete("t_guardianship", "driver_id", prefix);
        safeDelete("t_driver_health_profile", "driver_id", prefix);
        safeDelete("t_trajectory_projection", "trajectory_id", prefix);
        safeDelete("t_fleet_dashboard_projection", "fleet_id", prefix);
        safeDelete("t_trip", "trip_id", prefix);
        safeDelete("t_driver", "driver_id", prefix);
        safeDelete("t_vehicle", "vehicle_id", prefix);
        safeDelete("t_system_account", "account_id", prefix);
    }

    private void safeDelete(String table, String column, String prefix) {
        try {
            jdbc.update("DELETE FROM \"" + table + "\" WHERE \"" + column + "\" LIKE ?", prefix + "%");
        } catch (Exception ignored) {
            // 表不存在时忽略（首次运行 Flyway 尚未迁移的情况）
        }
    }
}
