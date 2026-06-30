-- ============================================
-- V3: 补全剩余 8 张表
-- 依据: docs/ood_infrastructure.md §3.1~§3.3
-- ============================================

-- AR-05：路怒语音存证
CREATE TABLE t_road_rage_voice_record (
    record_id            VARCHAR(36) PRIMARY KEY,
    version              INTEGER NOT NULL DEFAULT 0,
    alert_id             VARCHAR(36) NOT NULL UNIQUE,
    trip_id              VARCHAR(36) NOT NULL,
    driver_id            VARCHAR(36) NOT NULL,
    vehicle_id           VARCHAR(36) NOT NULL,
    started_at           TIMESTAMP NOT NULL,
    ended_at             TIMESTAMP,
    encrypted_file_path  TEXT,
    expiry_time          TIMESTAMP NOT NULL,
    is_sealed            BOOLEAN NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (trip_id) REFERENCES t_trip(trip_id)
);

-- E-03：驾驶员健康档案（独立表，driver_id PK+FK）
CREATE TABLE t_driver_health_profile (
    driver_id           VARCHAR(36) PRIMARY KEY,
    blood_type          VARCHAR(8),
    allergy_history     TEXT,
    chronic_history     TEXT,
    medication_history  TEXT,
    baseline_vitals     JSON,
    emergency_contact   JSON,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (driver_id) REFERENCES t_driver(driver_id)
);

-- 关联表：监护关系
CREATE TABLE t_guardianship (
    driver_id    VARCHAR(36) NOT NULL,
    account_id   VARCHAR(36) NOT NULL,
    granted_at   TIMESTAMP NOT NULL,
    permissions  JSON NOT NULL,
    grant_reason VARCHAR(32) NOT NULL,
    revoked_at   TIMESTAMP,
    PRIMARY KEY (driver_id, account_id, granted_at),
    FOREIGN KEY (driver_id)  REFERENCES t_driver(driver_id),
    FOREIGN KEY (account_id) REFERENCES t_system_account(account_id)
);

-- 集合表：生理快照
CREATE TABLE t_trip_physiological_snapshot (
    trip_id          VARCHAR(36) NOT NULL,
    timestamp        TIMESTAMP NOT NULL,
    heart_rate       INTEGER,
    blood_oxygen     DOUBLE PRECISION,
    emotion_index    DOUBLE PRECISION,
    respiratory_rate INTEGER,
    systolic_bp      INTEGER,
    diastolic_bp     INTEGER,
    fatigue_index    DOUBLE PRECISION,
    body_temperature DOUBLE PRECISION,
    source           VARCHAR(16) DEFAULT 'CASCADE',
    PRIMARY KEY (trip_id, timestamp),
    FOREIGN KEY (trip_id) REFERENCES t_trip(trip_id)
);

-- CQRS 投影表 P1：告警投影
CREATE TABLE t_alert_projection (
    alert_id    VARCHAR(36) PRIMARY KEY,
    driver_id   VARCHAR(36) NOT NULL,
    vehicle_id  VARCHAR(36) NOT NULL,
    fleet_id    VARCHAR(64),
    alert_type  VARCHAR(32) NOT NULL,
    risk_level  VARCHAR(16) NOT NULL,
    resolved_at TIMESTAMP,
    occurred_at TIMESTAMP NOT NULL,
    alert_msg   VARCHAR(256)
);

-- CQRS 投影表 P2：车队看板
CREATE TABLE t_fleet_dashboard_projection (
    fleet_id    VARCHAR(64) NOT NULL,
    risk_level  VARCHAR(16) NOT NULL,
    alert_type  VARCHAR(32) NOT NULL,
    alert_count INTEGER DEFAULT 0,
    driver_count INTEGER DEFAULT 0,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (fleet_id, risk_level, alert_type)
);

-- CQRS 投影表 P3：轨迹投影
CREATE TABLE t_trajectory_projection (
    trajectory_id VARCHAR(36) PRIMARY KEY,
    trip_id       VARCHAR(36) NOT NULL,
    vehicle_id    VARCHAR(36) NOT NULL,
    driver_id     VARCHAR(36) NOT NULL,
    gps_latitude  DOUBLE PRECISION,
    gps_longitude DOUBLE PRECISION,
    speed         DOUBLE PRECISION,
    recorded_at   TIMESTAMP NOT NULL
);

-- 事务性事件表
CREATE TABLE t_domain_event_outbox (
    event_id        VARCHAR(36) PRIMARY KEY,
    aggregate_id    VARCHAR(36) NOT NULL,
    event_type      VARCHAR(64) NOT NULL,
    event_payload   TEXT NOT NULL,
    published       BOOLEAN DEFAULT FALSE,
    last_attempt_at TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 死信队列表
CREATE TABLE t_domain_event_dlq (
    dlq_id            VARCHAR(36) PRIMARY KEY,
    original_event_id VARCHAR(36) NOT NULL,
    error_message     TEXT,
    moved_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 测试数据
-- ============================================

INSERT INTO t_driver_health_profile(driver_id, blood_type, allergy_history, chronic_history, medication_history, baseline_vitals, emergency_contact) VALUES
('d001-d2e3-4abc-9f01-123456789abc', 'O+', '青霉素过敏', NULL, NULL,
 '{"resting_heart_rate":72,"systolic_bp":120,"diastolic_bp":80}',
 '{"name":"张丽","phone":"13900000001"}'),
('d003-e4b2-6cde-7d03-345678901def', 'A+', NULL, '高血压', '硝苯地平 每日一次',
 '{"resting_heart_rate":78,"systolic_bp":135,"diastolic_bp":88}',
 '{"name":"王明","phone":"13900000003"}');

INSERT INTO t_guardianship(driver_id, account_id, granted_at, permissions, grant_reason) VALUES
('d001-d2e3-4abc-9f01-123456789abc', 'acct-001-aaa-bbb-ccc-111111111111', '2025-11-01 08:00:00',
 '{"can_view_alert":true,"can_view_health":true,"can_view_location":true}', '常规监护'),
('d003-e4b2-6cde-7d03-345678901def', 'acct-002-aaa-bbb-ccc-222222222222', '2025-11-15 09:00:00',
 '{"can_view_alert":true,"can_view_health":false,"can_view_location":true}', '常规监护');

INSERT INTO t_trip_physiological_snapshot(trip_id, timestamp, heart_rate, blood_oxygen, emotion_index, fatigue_index, body_temperature, source) VALUES
('t001-12ab-34cd-56ef-78901234abcd', '2025-12-20 08:05:00', 72, 98.5, 0.12, 0.05, 36.4, 'CASCADE'),
('t001-12ab-34cd-56ef-78901234abcd', '2025-12-20 08:10:00', 74, 98.3, 0.15, 0.07, 36.5, 'CASCADE'),
('t001-12ab-34cd-56ef-78901234abcd', '2025-12-20 09:00:00', 78, 97.8, 0.20, 0.12, 36.5, 'CASCADE'),
('t003-34cd-56ef-78ab-90123456cdef', '2025-12-20 09:05:00', 82, 97.5, 0.30, 0.18, 36.6, 'CASCADE'),
('t003-34cd-56ef-78ab-90123456cdef', '2025-12-20 10:00:00', 88, 96.8, 0.45, 0.35, 36.7, 'CASCADE'),
('t007-78ab-90cd-12ef-34567890abcd', '2025-12-20 14:05:00', 76, 97.2, 0.28, 0.22, 36.5, 'CASCADE'),
('t007-78ab-90cd-12ef-34567890abcd', '2025-12-20 14:55:00', 85, 96.5, 0.55, 0.50, 36.8, 'CASCADE');

INSERT INTO t_alert_projection(alert_id, driver_id, vehicle_id, fleet_id, alert_type, risk_level, resolved_at, occurred_at, alert_msg) VALUES
('a001-1111-2222-3333-aaaaaaaaaaaa', 'd001-d2e3-4abc-9f01-123456789abc', 'v001-c7e5-4ghi-a011-678901234hij', 'fleet-east-1', 'FATIGUE', 'WARNING', '2025-12-20 09:20:00', '2025-12-20 09:15:00', '驾驶员疲劳预警'),
('a002-2222-3333-4444-bbbbbbbbbbbb', 'd003-e4b2-6cde-7d03-345678901def', 'v003-e9a7-6ijk-c033-890123456jkl', 'fleet-west-1', 'RAGE', 'DANGER', NULL, '2025-12-20 10:30:00', '路怒检测'),
('a003-3333-4444-5555-cccccccccccc', 'd003-e4b2-6cde-7d03-345678901def', 'v003-e9a7-6ijk-c033-890123456jkl', 'fleet-west-1', 'DISTRACTION', 'WARNING', '2025-12-20 10:00:00', '2025-12-20 09:45:00', '分心驾驶'),
('a004-4444-5555-6666-dddddddddddd', 'd003-e4b2-6cde-7d03-345678901def', 'v003-e9a7-6ijk-c033-890123456jkl', 'fleet-west-1', 'FATIGUE', 'DANGER', NULL, '2025-12-20 15:00:00', '重度疲劳'),
('a005-5555-6666-7777-eeeeeeeeeeee', 'd001-d2e3-4abc-9f01-123456789abc', 'v001-c7e5-4ghi-a011-678901234hij', 'fleet-east-1', 'LIVING_LEFT', 'DANGER', NULL, '2025-12-20 10:05:00', '遗留活体检测');

INSERT INTO t_fleet_dashboard_projection(fleet_id, risk_level, alert_type, alert_count, driver_count) VALUES
('fleet-east-1', 'WARNING', 'FATIGUE', 1, 1),
('fleet-east-1', 'DANGER', 'LIVING_LEFT', 1, 1),
('fleet-west-1', 'DANGER', 'RAGE', 1, 1),
('fleet-west-1', 'WARNING', 'DISTRACTION', 1, 1),
('fleet-west-1', 'DANGER', 'FATIGUE', 1, 1);

INSERT INTO t_trajectory_projection(trajectory_id, trip_id, vehicle_id, driver_id, gps_latitude, gps_longitude, speed, recorded_at) VALUES
('trj-001', 't001-12ab-34cd-56ef-78901234abcd', 'v001-c7e5-4ghi-a011-678901234hij', 'd001-d2e3-4abc-9f01-123456789abc', 39.9042, 116.4074, 60.0, '2025-12-20 08:10:00'),
('trj-002', 't001-12ab-34cd-56ef-78901234abcd', 'v001-c7e5-4ghi-a011-678901234hij', 'd001-d2e3-4abc-9f01-123456789abc', 39.9100, 116.4200, 65.0, '2025-12-20 08:30:00'),
('trj-003', 't001-12ab-34cd-56ef-78901234abcd', 'v001-c7e5-4ghi-a011-678901234hij', 'd001-d2e3-4abc-9f01-123456789abc', 39.9200, 116.4300, 55.0, '2025-12-20 09:00:00'),
('trj-004', 't003-34cd-56ef-78ab-90123456cdef', 'v003-e9a7-6ijk-c033-890123456jkl', 'd003-e4b2-6cde-7d03-345678901def', 39.8900, 116.3800, 70.0, '2025-12-20 09:15:00'),
('trj-005', 't003-34cd-56ef-78ab-90123456cdef', 'v003-e9a7-6ijk-c033-890123456jkl', 'd003-e4b2-6cde-7d03-345678901def', 39.8950, 116.3900, 80.0, '2025-12-20 10:00:00');
