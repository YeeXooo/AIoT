-- ============================================
-- V2: 5 张核心业务表（基础设施层 V1）
-- 依据: docs/ood_infrastructure.md §3.1
-- 主键: 应用层生成 UUID
-- 乐观锁: version INTEGER
-- ============================================

CREATE TABLE t_driver (
    driver_id           VARCHAR(36) PRIMARY KEY,
    version             INTEGER NOT NULL DEFAULT 0,
    name                VARCHAR(128) NOT NULL,
    phone               VARCHAR(32),
    comprehensive_score INTEGER DEFAULT 100,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE t_vehicle (
    vehicle_id      VARCHAR(36) PRIMARY KEY,
    version         INTEGER NOT NULL DEFAULT 0,
    license_plate   VARCHAR(32) NOT NULL,
    vin             VARCHAR(64) NOT NULL,
    terminal_sn     VARCHAR(64) NOT NULL,
    fleet_id        VARCHAR(64),
    firmware_version VARCHAR(64),
    sensor_status   VARCHAR(32) DEFAULT 'ONLINE',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE t_trip (
    trip_id                 VARCHAR(36) PRIMARY KEY,
    version                 INTEGER NOT NULL DEFAULT 0,
    driver_id               VARCHAR(36) NOT NULL,
    vehicle_id              VARCHAR(36) NOT NULL,
    started_at              TIMESTAMP NOT NULL,
    ended_at                TIMESTAMP,
    hard_braking_count      INTEGER DEFAULT 0,
    hard_acceleration_count INTEGER DEFAULT 0,
    score_value             INTEGER,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (driver_id)  REFERENCES t_driver(driver_id),
    FOREIGN KEY (vehicle_id) REFERENCES t_vehicle(vehicle_id)
);

CREATE TABLE t_safety_alert_event (
    alert_id    VARCHAR(36) PRIMARY KEY,
    version     INTEGER NOT NULL DEFAULT 0,
    trip_id     VARCHAR(36) NOT NULL,
    driver_id   VARCHAR(36) NOT NULL,
    vehicle_id  VARCHAR(36) NOT NULL,
    alert_type  VARCHAR(32) NOT NULL,
    risk_level  VARCHAR(16) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    alert_msg   VARCHAR(256),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (trip_id) REFERENCES t_trip(trip_id)
);

CREATE TABLE t_system_account (
    account_id VARCHAR(36) PRIMARY KEY,
    version    INTEGER NOT NULL DEFAULT 0,
    phone      VARCHAR(32) NOT NULL,
    role       VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 测试数据 — 5 名驾驶员,5 辆车,7 次行程,5 条告警,3 个账户
-- ============================================

INSERT INTO t_driver(driver_id, name, phone, comprehensive_score) VALUES
('d001-d2e3-4abc-9f01-123456789abc', '张伟', '13800000001', 88),
('d002-f3a1-5bcd-8e02-234567890bcd', '李强', '13800000002', 92),
('d003-e4b2-6cde-7d03-345678901def', '王芳', '13800000003', 75),
('d004-a5c3-7def-6a04-456789012efg', '赵明', '13800000004', 60),
('d005-b6d4-8ef0-5b05-567890123fgh', '刘洋', '13800000005', 95);

INSERT INTO t_vehicle(vehicle_id, license_plate, vin, terminal_sn, fleet_id, firmware_version, sensor_status) VALUES
('v001-c7e5-4ghi-a011-678901234hij', '辽A10001', 'LSV0000001', 'TERM-001', 'fleet-east-1', 'v2.3.1', 'ONLINE'),
('v002-d8f6-5hij-b022-789012345ijk', '辽A10002', 'LSV0000002', 'TERM-002', 'fleet-east-1', 'v2.3.0', 'ONLINE'),
('v003-e9a7-6ijk-c033-890123456jkl', '辽A10003', 'LSV0000003', 'TERM-003', 'fleet-west-1', 'v2.3.1', 'ONLINE'),
('v004-f0b8-7jkl-d044-901234567klm', '辽A10004', 'LSV0000004', 'TERM-004', 'fleet-south-1', 'v2.2.5', 'FAULT'),
('v005-a1c9-8klm-e055-012345678lmn', '辽A10005', 'LSV0000005', 'TERM-005', 'fleet-east-1', 'v2.3.1', 'ONLINE');

INSERT INTO t_trip(trip_id, driver_id, vehicle_id, started_at, ended_at, hard_braking_count, hard_acceleration_count, score_value) VALUES
('t001-12ab-34cd-56ef-78901234abcd', 'd001-d2e3-4abc-9f01-123456789abc', 'v001-c7e5-4ghi-a011-678901234hij', '2025-12-20 08:00:00', '2025-12-20 10:00:00', 2, 3, 78),
('t002-23bc-45de-67fa-89012345bcde', 'd002-f3a1-5bcd-8e02-234567890bcd', 'v002-d8f6-5hij-b022-789012345ijk', '2025-12-20 08:15:00', '2025-12-20 09:45:00', 1, 1, 90),
('t003-34cd-56ef-78ab-90123456cdef', 'd003-e4b2-6cde-7d03-345678901def', 'v003-e9a7-6ijk-c033-890123456jkl', '2025-12-20 09:00:00', '2025-12-20 11:30:00', 3, 5, 55),
('t004-45de-67fa-89bc-01234567defa', 'd004-a5c3-7def-6a04-456789012efg', 'v004-f0b8-7jkl-d044-901234567klm', '2025-12-20 07:30:00', NULL, 1, 0, NULL),
('t005-56ef-78ab-90cd-12345678efab', 'd005-b6d4-8ef0-5b05-567890123fgh', 'v005-a1c9-8klm-e055-012345678lmn', '2025-12-20 10:00:00', '2025-12-20 12:15:00', 0, 1, 95),
('t006-67fa-89bc-01de-23456789fabc', 'd001-d2e3-4abc-9f01-123456789abc', 'v001-c7e5-4ghi-a011-678901234hij', '2025-12-20 13:00:00', NULL, 0, 0, NULL),
('t007-78ab-90cd-12ef-34567890abcd', 'd003-e4b2-6cde-7d03-345678901def', 'v003-e9a7-6ijk-c033-890123456jkl', '2025-12-20 14:00:00', '2025-12-20 15:30:00', 4, 6, 42);

INSERT INTO t_safety_alert_event(alert_id, trip_id, driver_id, vehicle_id, alert_type, risk_level, occurred_at, alert_msg) VALUES
('a001-1111-2222-3333-aaaaaaaaaaaa', 't001-12ab-34cd-56ef-78901234abcd', 'd001-d2e3-4abc-9f01-123456789abc', 'v001-c7e5-4ghi-a011-678901234hij', 'FATIGUE', 'L2_WARNING', '2025-12-20 09:15:00', '驾驶员疲劳预警 — 连续YAW>3s'),
('a002-2222-3333-4444-bbbbbbbbbbbb', 't003-34cd-56ef-78ab-90123456cdef', 'd003-e4b2-6cde-7d03-345678901def', 'v003-e9a7-6ijk-c033-890123456jkl', 'ROAD_RAGE', 'L3_CRITICAL', '2025-12-20 10:30:00', '路怒检测 — 声压>85dB + 谩骂关键词'),
('a003-3333-4444-5555-cccccccccccc', 't003-34cd-56ef-78ab-90123456cdef', 'd003-e4b2-6cde-7d03-345678901def', 'v003-e9a7-6ijk-c033-890123456jkl', 'DISTRACTION', 'L2_WARNING', '2025-12-20 09:45:00', '分心驾驶 — 手持电话 > 3秒'),
('a004-4444-5555-6666-dddddddddddd', 't007-78ab-90cd-12ef-34567890abcd', 'd003-e4b2-6cde-7d03-345678901def', 'v003-e9a7-6ijk-c033-890123456jkl', 'FATIGUE', 'L3_CRITICAL', '2025-12-20 15:00:00', '重度疲劳 — 闭眼>1.5s + 频繁点头'),
('a005-5555-6666-7777-eeeeeeeeeeee', 't001-12ab-34cd-56ef-78901234abcd', 'd001-d2e3-4abc-9f01-123456789abc', 'v001-c7e5-4ghi-a011-678901234hij', 'LIFE_DETECTION', 'L3_CRITICAL', '2025-12-20 10:05:00', '锁车后雷达检测到后排遗留生命体征微动');

INSERT INTO t_system_account(account_id, phone, role) VALUES
('acct-001-aaa-bbb-ccc-111111111111', '13900000001', 'FAMILY'),
('acct-002-aaa-bbb-ccc-222222222222', '13900000002', 'FAMILY'),
('acct-003-aaa-bbb-ccc-333333333333', '18800000001', 'MANAGER');
