-- Phase 4: optimistic concurrency and idempotent Outbox identity.
ALTER TABLE work_orders
    ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0 COMMENT 'optimistic lock version' AFTER rating;

ALTER TABLE knowledge_outbox
    ADD COLUMN event_id CHAR(36) NULL COMMENT 'stable idempotency key' AFTER id,
    ADD COLUMN locked_at DATETIME NULL AFTER next_retry_at,
    ADD COLUMN completed_at DATETIME NULL AFTER locked_at,
    ADD UNIQUE KEY uk_knowledge_outbox_event_id (event_id);

UPDATE knowledge_outbox
SET event_id = UUID()
WHERE event_id IS NULL;

ALTER TABLE knowledge_outbox
    MODIFY COLUMN event_id CHAR(36) NOT NULL COMMENT 'stable idempotency key';
