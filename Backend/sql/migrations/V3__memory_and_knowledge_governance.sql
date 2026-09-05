-- Phase 3: memory consent/retention and versioned vector projection metadata.
-- Java/MySQL remains the source of truth; vector rows are rebuilt from published documents.

CREATE TABLE IF NOT EXISTS memory_consents (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    user_id         BIGINT NOT NULL,
    memory_scope    VARCHAR(32) NOT NULL COMMENT 'WORKING|LONG_TERM|BUSINESS_FACT',
    granted         TINYINT(1) NOT NULL DEFAULT 0,
    granted_at      DATETIME NULL,
    revoked_at      DATETIME NULL,
    policy_version  VARCHAR(32) NOT NULL DEFAULT 'v1',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_memory_consent_user_scope (user_id, memory_scope),
    INDEX idx_memory_consent_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户记忆授权与撤回记录';

CREATE TABLE IF NOT EXISTS user_memories (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    user_id             BIGINT NOT NULL,
    memory_scope        VARCHAR(32) NOT NULL COMMENT 'LONG_TERM|BUSINESS_FACT',
    memory_key          VARCHAR(128) NOT NULL,
    value_json          JSON NOT NULL,
    masked_preview      VARCHAR(512) NULL COMMENT '仅供客服/审计界面展示的脱敏预览',
    source_session_id   VARCHAR(128) NULL,
    source_type         VARCHAR(32) NOT NULL DEFAULT 'USER_CONFIRMED',
    expires_at          DATETIME NULL,
    deleted_at          DATETIME NULL,
    version             INT NOT NULL DEFAULT 1,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_memory_key (user_id, memory_scope, memory_key),
    INDEX idx_user_memory_active (user_id, memory_scope, deleted_at, expires_at),
    CONSTRAINT chk_user_memory_scope CHECK (memory_scope IN ('LONG_TERM', 'BUSINESS_FACT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分层用户记忆，支持过期、删除和版本';

CREATE TABLE IF NOT EXISTS knowledge_index_manifests (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    dataset_id          VARCHAR(64) NOT NULL,
    document_id         BIGINT NOT NULL,
    document_version    INT NOT NULL,
    embedding_model     VARCHAR(128) NOT NULL,
    chunk_strategy      VARCHAR(32) NOT NULL,
    vector_collection    VARCHAR(128) NOT NULL,
    status              VARCHAR(24) NOT NULL COMMENT 'BUILDING|READY|RETIRED|FAILED',
    acl_hash             CHAR(64) NULL,
    source_hash          CHAR(64) NULL,
    activated_at         DATETIME NULL,
    retired_at           DATETIME NULL,
    last_error           VARCHAR(1000) NULL,
    created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_knowledge_projection_version (dataset_id, document_id, document_version, embedding_model),
    INDEX idx_knowledge_projection_active (dataset_id, status, activated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识向量索引可重建投影清单';
