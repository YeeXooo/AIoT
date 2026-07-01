-- ============================================================
-- 领域事件 Outbox 表（domain_event_outbox）
-- 依据：docs/ood_infrastructure.md §3.3.2
-- ============================================================

CREATE TABLE domain_event_outbox (
    event_id        VARCHAR(64)     NOT NULL,
    event_type      VARCHAR(128)    NOT NULL,
    aggregate_id    VARCHAR(256)    NOT NULL,
    aggregate_type  VARCHAR(64)     NOT NULL,
    payload         TEXT            NOT NULL,
    occurred_at     TIMESTAMP       NOT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    last_attempt_at TIMESTAMP       NULL,
    published       BOOLEAN         NOT NULL DEFAULT FALSE,
    retry_count     INTEGER         NOT NULL DEFAULT 0,
    last_error      TEXT            NULL,
    PRIMARY KEY (event_id)
);

-- 投递器轮询索引：ORDER BY last_attempt_at ASC NULLS FIRST
CREATE INDEX idx_outbox_polling
    ON domain_event_outbox (published, last_attempt_at);

-- 审计追踪索引：按聚合根追溯事件
CREATE INDEX idx_outbox_aggregate
    ON domain_event_outbox (aggregate_type, aggregate_id);

-- 事件重放索引：按事件类型 + 时间范围重放
CREATE INDEX idx_outbox_replay
    ON domain_event_outbox (event_type, occurred_at);

-- ============================================================
-- 领域事件死信表（domain_event_dlq）
-- 依据：docs/ood_infrastructure.md §3.3.3
-- ============================================================

CREATE TABLE domain_event_dlq (
    dlq_id          VARCHAR(64)     NOT NULL,
    event_id        VARCHAR(64)     NOT NULL,
    event_type      VARCHAR(128)    NOT NULL,
    aggregate_id    VARCHAR(256)    NOT NULL,
    payload         TEXT            NOT NULL,
    occurred_at     TIMESTAMP       NOT NULL,
    moved_at        TIMESTAMP       NOT NULL DEFAULT NOW(),
    retry_count     INTEGER         NOT NULL,
    last_error      TEXT            NOT NULL,
    PRIMARY KEY (dlq_id)
);
