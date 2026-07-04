-- ============================================
-- V5: 为 t_system_account 增加 password_hash 列
-- 并更新种子数据中的密码哈希
-- ============================================

ALTER TABLE t_system_account
    ADD COLUMN password_hash VARCHAR(128);

-- family001 (13900000001): 123456
UPDATE t_system_account SET password_hash = '$2a$10$fJ.abnvFj9CukUMDPRi01e2rUJGznt4jPCeZbCh0u72/nPewPmAI6'
    WHERE phone = '13900000001';

-- family002 (13900000002): pass123
UPDATE t_system_account SET password_hash = '$2b$10$OsX1NXZYX1SVHGcLVjtIUuWaG2XjarJT8jj1LzcpV0oYs/Livzd/K'
    WHERE phone = '13900000002';

-- manager001 (18800000001): pass123
UPDATE t_system_account SET password_hash = '$2b$10$OsX1NXZYX1SVHGcLVjtIUuWaG2XjarJT8jj1LzcpV0oYs/Livzd/K'
    WHERE phone = '18800000001';
