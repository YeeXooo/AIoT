INSERT INTO t_driver(driver_id, version, name, phone, comprehensive_score, created_at, updated_at) VALUES
('d001-d2e3-4abc-9f01-123456789abc', 0, '张伟', '13800000001', 88, '2025-11-01 00:00:00', '2025-12-20 08:00:00'),
('d002-f3a1-5bcd-8e02-234567890bcd', 0, '李强', '13800000002', 92, '2025-11-01 00:00:00', '2025-12-20 08:00:00'),
('d003-e4b2-6cde-7d03-345678901def', 0, '王芳', '13800000003', 75, '2025-11-01 00:00:00', '2025-12-20 08:00:00'),
('d004-a5c3-7def-6a04-456789012efg', 0, '赵明', '13800000004', 60, '2025-11-01 00:00:00', '2025-12-20 08:00:00'),
('d005-b6d4-8ef0-5b05-567890123fgh', 0, '刘洋', '13800000005', 95, '2025-11-01 00:00:00', '2025-12-20 08:00:00');

INSERT INTO t_vehicle(vehicle_id, version, license_plate, vin, terminal_sn, fleet_id, firmware_version, sensor_status, created_at, updated_at) VALUES
('v001-c7e5-4ghi-a011-678901234hij', 0, '辽A10001', 'LSV0000001', 'TERM-001', 'fleet-east-1', 'v2.3.1', 'ONLINE', '2025-12-01 00:00:00', '2025-12-20 08:00:00'),
('v002-d8f6-5hij-b022-789012345ijk', 0, '辽A10002', 'LSV0000002', 'TERM-002', 'fleet-east-1', 'v2.3.0', 'ONLINE', '2025-12-01 00:00:00', '2025-12-20 08:00:00'),
('v003-e9a7-6ijk-c033-890123456jkl', 0, '辽A10003', 'LSV0000003', 'TERM-003', 'fleet-west-1', 'v2.3.1', 'ONLINE', '2025-12-01 00:00:00', '2025-12-20 08:00:00'),
('v004-f0b8-7jkl-d044-901234567klm', 0, '辽A10004', 'LSV0000004', 'TERM-004', 'fleet-south-1', 'v2.2.5', 'FAULT', '2025-12-01 00:00:00', '2025-12-20 08:00:00'),
('v005-a1c9-8klm-e055-012345678lmn', 0, '辽A10005', 'LSV0000005', 'TERM-005', 'fleet-east-1', 'v2.3.1', 'ONLINE', '2025-12-01 00:00:00', '2025-12-20 08:00:00');

INSERT INTO t_trip(trip_id, version, driver_id, vehicle_id, started_at, ended_at, hard_braking_count, hard_acceleration_count, score_value, created_at, updated_at) VALUES
('t001-12ab-34cd-56ef-78901234abcd', 0, 'd001-d2e3-4abc-9f01-123456789abc', 'v001-c7e5-4ghi-a011-678901234hij', '2025-12-20 08:00:00', '2025-12-20 10:00:00', 2, 3, 78, '2025-12-20 08:00:00', '2025-12-20 10:00:00'),
('t002-23bc-45de-67fa-89012345bcde', 0, 'd002-f3a1-5bcd-8e02-234567890bcd', 'v002-d8f6-5hij-b022-789012345ijk', '2025-12-20 08:15:00', '2025-12-20 09:45:00', 1, 1, 90, '2025-12-20 08:15:00', '2025-12-20 09:45:00'),
('t003-34cd-56ef-78ab-90123456cdef', 0, 'd003-e4b2-6cde-7d03-345678901def', 'v003-e9a7-6ijk-c033-890123456jkl', '2025-12-20 09:00:00', '2025-12-20 11:30:00', 3, 5, 55, '2025-12-20 09:00:00', '2025-12-20 11:30:00'),
('t004-45de-67fa-89bc-01234567defa', 0, 'd004-a5c3-7def-6a04-456789012efg', 'v004-f0b8-7jkl-d044-901234567klm', '2025-12-20 07:30:00', NULL, 1, 0, NULL, '2025-12-20 07:30:00', '2025-12-20 07:30:00'),
('t005-56ef-78ab-90cd-12345678efab', 0, 'd005-b6d4-8ef0-5b05-567890123fgh', 'v005-a1c9-8klm-e055-012345678lmn', '2025-12-20 10:00:00', '2025-12-20 12:15:00', 0, 1, 95, '2025-12-20 10:00:00', '2025-12-20 12:15:00'),
('t006-67fa-89bc-01de-23456789fabc', 0, 'd001-d2e3-4abc-9f01-123456789abc', 'v001-c7e5-4ghi-a011-678901234hij', '2025-12-20 13:00:00', NULL, 0, 0, NULL, '2025-12-20 13:00:00', '2025-12-20 13:00:00'),
('t007-78ab-90cd-12ef-34567890abcd', 0, 'd003-e4b2-6cde-7d03-345678901def', 'v003-e9a7-6ijk-c033-890123456jkl', '2025-12-20 14:00:00', '2025-12-20 15:30:00', 4, 6, 42, '2025-12-20 14:00:00', '2025-12-20 15:30:00');

INSERT INTO t_safety_alert_event(alert_id, version, trip_id, driver_id, vehicle_id, alert_type, risk_level, occurred_at, alert_msg, created_at, updated_at) VALUES
('a001-1111-2222-3333-aaaaaaaaaaaa', 0, 't001-12ab-34cd-56ef-78901234abcd', 'd001-d2e3-4abc-9f01-123456789abc', 'v001-c7e5-4ghi-a011-678901234hij', 'FATIGUE', 'L2_WARNING', '2025-12-20 09:15:00', '驾驶员疲劳预警', '2025-12-20 09:15:00', '2025-12-20 09:15:00'),
('a002-2222-3333-4444-bbbbbbbbbbbb', 0, 't003-34cd-56ef-78ab-90123456cdef', 'd003-e4b2-6cde-7d03-345678901def', 'v003-e9a7-6ijk-c033-890123456jkl', 'ROAD_RAGE', 'L3_CRITICAL', '2025-12-20 10:30:00', '路怒检测', '2025-12-20 10:30:00', '2025-12-20 10:30:00'),
('a003-3333-4444-5555-cccccccccccc', 0, 't003-34cd-56ef-78ab-90123456cdef', 'd003-e4b2-6cde-7d03-345678901def', 'v003-e9a7-6ijk-c033-890123456jkl', 'DISTRACTION', 'L2_WARNING', '2025-12-20 09:45:00', '分心驾驶', '2025-12-20 09:45:00', '2025-12-20 09:45:00'),
('a004-4444-5555-6666-dddddddddddd', 0, 't007-78ab-90cd-12ef-34567890abcd', 'd003-e4b2-6cde-7d03-345678901def', 'v003-e9a7-6ijk-c033-890123456jkl', 'FATIGUE', 'L3_CRITICAL', '2025-12-20 15:00:00', '重度疲劳', '2025-12-20 15:00:00', '2025-12-20 15:00:00'),
('a005-5555-6666-7777-eeeeeeeeeeee', 0, 't001-12ab-34cd-56ef-78901234abcd', 'd001-d2e3-4abc-9f01-123456789abc', 'v001-c7e5-4ghi-a011-678901234hij', 'LIFE_DETECTION', 'L3_CRITICAL', '2025-12-20 10:05:00', '遗留活体检测', '2025-12-20 10:05:00', '2025-12-20 10:05:00');

INSERT INTO t_system_account(account_id, version, phone, role, created_at, updated_at) VALUES
('acct-001-aaa-bbb-ccc-111111111111', 0, '13900000001', 'FAMILY', '2025-11-01 00:00:00', '2025-11-01 00:00:00'),
('acct-002-aaa-bbb-ccc-222222222222', 0, '13900000002', 'FAMILY', '2025-11-01 00:00:00', '2025-11-01 00:00:00'),
('acct-003-aaa-bbb-ccc-333333333333', 0, '18800000001', 'MANAGER', '2025-11-01 00:00:00', '2025-11-01 00:00:00');

INSERT INTO t_driver_health_profile(driver_id, blood_type, allergy_history, chronic_history, medication_history, baseline_vitals, emergency_contact, created_at, updated_at) VALUES
('d001-d2e3-4abc-9f01-123456789abc', 'O+', '青霉素过敏', NULL, NULL,
 '{"resting_heart_rate":72,"systolic_bp":120,"diastolic_bp":80}',
 '{"name":"张丽","phone":"13900000001"}', '2025-12-20 08:00:00', '2025-12-20 08:00:00'),
('d003-e4b2-6cde-7d03-345678901def', 'A+', NULL, '高血压', '硝苯地平 每日一次',
 '{"resting_heart_rate":78,"systolic_bp":135,"diastolic_bp":88}',
 '{"name":"王明","phone":"13900000003"}', '2025-12-20 08:00:00', '2025-12-20 08:00:00');

INSERT INTO t_guardianship(driver_id, account_id, granted_at, permissions, grant_reason, revoked_at) VALUES
('d001-d2e3-4abc-9f01-123456789abc', 'acct-001-aaa-bbb-ccc-111111111111', '2025-11-01 08:00:00',
 '{"can_view_alert":true,"can_view_health":true,"can_view_location":true}', '常规监护', NULL),
('d003-e4b2-6cde-7d03-345678901def', 'acct-002-aaa-bbb-ccc-222222222222', '2025-11-15 09:00:00',
 '{"can_view_alert":true,"can_view_health":false,"can_view_location":true}', '常规监护', NULL);

INSERT INTO t_trip_physiological_snapshot(trip_id, timestamp, heart_rate, blood_oxygen, emotion_index, fatigue_index, body_temperature, source) VALUES
('t001-12ab-34cd-56ef-78901234abcd', '2025-12-20 08:05:00', 72, 98.5, 0.12, 0.05, 36.4, 'CASCADE'),
('t001-12ab-34cd-56ef-78901234abcd', '2025-12-20 08:10:00', 74, 98.3, 0.15, 0.07, 36.5, 'CASCADE');

INSERT INTO t_alert_projection(alert_id, driver_id, vehicle_id, fleet_id, alert_type, risk_level, resolved_at, occurred_at, alert_msg) VALUES
('a001-1111-2222-3333-aaaaaaaaaaaa', 'd001-d2e3-4abc-9f01-123456789abc', 'v001-c7e5-4ghi-a011-678901234hij', 'fleet-east-1', 'FATIGUE', 'L2_WARNING', '2025-12-20 09:20:00', '2025-12-20 09:15:00', '驾驶员疲劳预警'),
('a002-2222-3333-4444-bbbbbbbbbbbb', 'd003-e4b2-6cde-7d03-345678901def', 'v003-e9a7-6ijk-c033-890123456jkl', 'fleet-west-1', 'ROAD_RAGE', 'L3_CRITICAL', NULL, '2025-12-20 10:30:00', '路怒检测'),
('a005-5555-6666-7777-eeeeeeeeeeee', 'd001-d2e3-4abc-9f01-123456789abc', 'v001-c7e5-4ghi-a011-678901234hij', 'fleet-east-1', 'LIFE_DETECTION', 'L3_CRITICAL', NULL, '2025-12-20 10:05:00', '遗留活体检测');

INSERT INTO t_fleet_dashboard_projection(fleet_id, risk_level, alert_type, alert_count, driver_count, updated_at) VALUES
('fleet-east-1', 'L2_WARNING', 'FATIGUE', 1, 1, '2025-12-20 10:00:00'),
('fleet-east-1', 'L3_CRITICAL', 'LIFE_DETECTION', 1, 1, '2025-12-20 10:00:00'),
('fleet-west-1', 'L3_CRITICAL', 'ROAD_RAGE', 1, 1, '2025-12-20 10:00:00');

INSERT INTO t_trajectory_projection(trajectory_id, trip_id, vehicle_id, driver_id, gps_latitude, gps_longitude, speed, recorded_at) VALUES
('trj-001', 't001-12ab-34cd-56ef-78901234abcd', 'v001-c7e5-4ghi-a011-678901234hij', 'd001-d2e3-4abc-9f01-123456789abc', 39.9042, 116.4074, 60.0, '2025-12-20 08:10:00'),
('trj-002', 't001-12ab-34cd-56ef-78901234abcd', 'v001-c7e5-4ghi-a011-678901234hij', 'd001-d2e3-4abc-9f01-123456789abc', 39.9100, 116.4200, 65.0, '2025-12-20 08:30:00'),
('trj-004', 't003-34cd-56ef-78ab-90123456cdef', 'v003-e9a7-6ijk-c033-890123456jkl', 'd003-e4b2-6cde-7d03-345678901def', 39.8900, 116.3800, 70.0, '2025-12-20 09:15:00');
