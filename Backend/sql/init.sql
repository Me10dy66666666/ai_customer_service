-- Create database if not exists
CREATE DATABASE IF NOT EXISTS ai_customer_service DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE ai_customer_service;

-- 6.1 用户表（users）
CREATE TABLE IF NOT EXISTS `users` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `password` VARCHAR(100) NOT NULL COMMENT '密码（加密存储）',
  `nickname` VARCHAR(50) COMMENT '昵称',
  `phone` VARCHAR(20) COMMENT '手机号',
  `email` VARCHAR(100) COMMENT '邮箱',
  `user_type` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '@Deprecated 已废弃，鉴权统一使用 user_roles/roles 表',
  `status` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_phone` (`phone`),
  UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 6.2 知识库表（knowledge_base）
CREATE TABLE IF NOT EXISTS `knowledge_base` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '知识ID',
  `title` VARCHAR(200) NOT NULL COMMENT '标题',
  `content` TEXT NOT NULL COMMENT '内容',
  `category` VARCHAR(50) NOT NULL COMMENT '分类',
  `file_type` VARCHAR(20) COMMENT '文件类型：PDF, Excel, Word, Text',
  `file_path` VARCHAR(255) COMMENT '文件存储路径',
  `dify_document_id` VARCHAR(255) COMMENT 'Dify文档ID',
  `rag_document_id` VARCHAR(64) NULL COMMENT 'rag-service 文档ID',
  `vector_store` VARCHAR(32) NULL COMMENT '向量库类型',
  `embedding_model` VARCHAR(64) NULL COMMENT '向量模型',
  `index_status` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '索引状态：0待入库，1已入库，2失败，3已删除',
  `index_error` TEXT NULL COMMENT '索引错误信息',
  `keywords` VARCHAR(255) COMMENT '关键词',
  `status` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_category` (`category`),
  INDEX `idx_keywords` (`keywords`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库表';

-- 6.3 咨询日志表（consultation_logs）
CREATE TABLE IF NOT EXISTS `consultation_logs` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `session_id` VARCHAR(64) NOT NULL COMMENT '会话ID(UUID)',
  `user_id` BIGINT(20) COMMENT '用户ID(未注册用户为空)',
  `agent_id` BIGINT(20) COMMENT '处理客服ID(人工接手后关联)',
  `user_input` TEXT NOT NULL COMMENT '用户输入',
  `ai_response` TEXT NOT NULL COMMENT 'AI回复',
  `dify_conversation_id` VARCHAR(100) COMMENT 'Dify会话ID',
  `intent` VARCHAR(100) COMMENT '识别的意图',
  `channel` VARCHAR(50) NOT NULL COMMENT '接入渠道：微信服务号，网页',
  `duration` INT COMMENT '对话时长（秒）',
  `satisfaction` TINYINT(1) COMMENT '满意度评分：1-非常不满意(差评)，2-不满意，3-一般，4-满意，5-非常满意(点赞)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_agent_id` (`agent_id`),
  INDEX `idx_create_time` (`create_time`),
  INDEX `idx_channel` (`channel`),
  INDEX `idx_session_create` (`session_id`, `create_time`),
  INDEX `idx_user_create` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='咨询日志表';

ALTER TABLE consultation_logs DROP COLUMN user_type;

-- 6.4 历史订单表（historical_orders）
CREATE TABLE IF NOT EXISTS `historical_orders` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `user_id` BIGINT(20) NOT NULL COMMENT '用户ID',
  `order_no` VARCHAR(50) NOT NULL COMMENT '订单号',
  `product_name` VARCHAR(200) NOT NULL COMMENT '产品名称',
  `product_model` VARCHAR(100) COMMENT '产品型号',
  `quantity` INT NOT NULL DEFAULT 1 COMMENT '数量',
  `price` DECIMAL(10,2) NOT NULL COMMENT '价格',
  `total_amount` DECIMAL(10,2) NOT NULL COMMENT '总金额',
  `order_status` VARCHAR(50) NOT NULL COMMENT '订单状态',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史订单表';

-- 6.5 服务记录表（service_records）
CREATE TABLE IF NOT EXISTS `service_records` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '服务记录ID',
  `user_id` BIGINT(20) NOT NULL COMMENT '用户ID',
  `service_type` VARCHAR(50) NOT NULL COMMENT '服务类型',
  `service_content` TEXT NOT NULL COMMENT '服务内容',
  `service_result` TEXT COMMENT '服务结果',
  `service_status` VARCHAR(50) NOT NULL COMMENT '服务状态',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_service_type` (`service_type`),
  INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务记录表';

-- 6.6 用户画像表（user_profiles）
CREATE TABLE IF NOT EXISTS `user_profiles` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '画像ID',
  `user_id` BIGINT(20) NOT NULL COMMENT '用户ID',
  `session_id` VARCHAR(64) COMMENT '关联会话ID',
  `user_type` VARCHAR(255) COMMENT '用户类型',
  `satisfaction_score` DOUBLE COMMENT '满意度评分',
  `preferred_products` TEXT COMMENT '偏好产品',
  `purchase_frequency` INT COMMENT '购买频率',
  `total_spending` DECIMAL(10,2) COMMENT '总消费金额',
  `service_times` INT COMMENT '服务次数',
  `last_purchase_time` DATETIME COMMENT '最后购买时间',
  `last_service_time` DATETIME COMMENT '最后服务时间',
  `tags` VARCHAR(255) COMMENT '用户标签',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`),
  UNIQUE KEY `uk_session_id` (`session_id`),
  INDEX `idx_tags` (`tags`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户画像表';

-- 6.7 角色权限表（roles_and_permissions）
CREATE TABLE IF NOT EXISTS `roles` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_name` VARCHAR(50) NOT NULL COMMENT '角色名称',
  `description` VARCHAR(200) COMMENT '角色描述',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_name` (`role_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

CREATE TABLE IF NOT EXISTS `user_roles` (
  `user_id` BIGINT(20) NOT NULL COMMENT '用户ID',
  `role_id` BIGINT(20) NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`user_id`, `role_id`),
  FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';


-- 6.8 工单表（work_orders）— 旧表删除重建
DROP TABLE IF EXISTS `work_orders`;
CREATE TABLE `work_orders` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '工单ID',
  `user_id` BIGINT(20) NOT NULL COMMENT '用户ID',
  `title` VARCHAR(200) NOT NULL COMMENT '标题',
  `description` TEXT NOT NULL COMMENT '描述',
  `type` VARCHAR(50) NOT NULL COMMENT '类型：售前/售后（由Dify biz_tag覆盖）',
  `priority` VARCHAR(20) NOT NULL DEFAULT 'medium' COMMENT '优先级：high/medium/low',
  `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态：pending/processing/completed/cancelled',
  `handler_id` BIGINT(20) COMMENT '处理人ID',
  `user_phone` VARCHAR(20) COMMENT '用户手机号（创建时快照）',
  `user_nickname` VARCHAR(50) COMMENT '用户昵称（创建时快照）',
  `result` TEXT COMMENT '处理结果',
  `tags` VARCHAR(500) COMMENT 'AI自动标签，逗号分隔',
  `summary` TEXT COMMENT 'Dify AI对话摘要',
  `session_id` VARCHAR(64) COMMENT '关联会话ID',
  `matching_skill` VARCHAR(64) COMMENT '派单匹配的技能标签',
  `dispatch_confidence` DECIMAL(3,2) COMMENT '派单置信度 0.00~1.00',
  `biz_tag` VARCHAR(50) COMMENT 'Dify返回的业务标签 pre_sales/after_sales 用于SLA路由',
  `emotion_level` VARCHAR(32) COMMENT '情绪评级 anxious/angry/neutral/satisfied',
  `sla_deadline` DATETIME COMMENT 'SLA解决时效截止时间',
  `response_deadline` DATETIME COMMENT '响应时效截止时间（客服首次回复DDL）',
  `responded_at` DATETIME COMMENT '客服实际首次回复时间',
  `sla_paused` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'SLA是否已暂停',
  `effective_response_seconds` INT COMMENT '有效响应耗时（秒，扣除暂停和非工作时间）',
  `effective_resolution_seconds` INT COMMENT '有效解决耗时（秒，扣除暂停和非工作时间）',
  `first_responder_id` BIGINT(20) COMMENT '首次响应客服ID',
  `resolver_id` BIGINT(20) COMMENT '最终解决人ID',
  `exclude_from_sla` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否排除SLA考核：1=排除',
  `rating` TINYINT(1) NULL COMMENT '工单评价 1-5星，NULL=未评价',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_handler_id` (`handler_id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_create_time` (`create_time`),
  INDEX `idx_session_id` (`session_id`),
  INDEX `idx_handler_status` (`handler_id`, `status`),
  INDEX `idx_tags` (`tags`),
  INDEX `idx_matching_skill` (`matching_skill`),
  INDEX `idx_sla_deadline` (`sla_deadline`),
  INDEX `idx_biz_tag` (`biz_tag`),
  INDEX `idx_first_responder` (`first_responder_id`),
  INDEX `idx_resolver` (`resolver_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单表';

CREATE TABLE chat_messages (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id  VARCHAR(64)   NOT NULL COMMENT '会话标识',
    sender_type VARCHAR(16)   NOT NULL COMMENT 'USER | AGENT | SYSTEM',
    sender_id   BIGINT        NULL     COMMENT '发送者ID',
    content     TEXT          NOT NULL COMMENT '消息内容',
    message_seq INT           NOT NULL DEFAULT 0 COMMENT '会话内消息序号',
    satisfaction TINYINT(1)   NULL     COMMENT '服务评价 1-5星，NULL=未评价，仅sender_type=AGENT的记录写入',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session_seq (session_id, message_seq)
) COMMENT '客服与用户人工对话记录';

-- ===== 工单流转日志表 =====
DROP TABLE IF EXISTS work_order_transfer_log;
CREATE TABLE work_order_transfer_log (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    work_order_id    BIGINT       NOT NULL COMMENT '工单ID',
    from_handler_id  BIGINT       COMMENT '原处理人ID（首次认领时为NULL）',
    to_handler_id    BIGINT       NOT NULL COMMENT '目标处理人ID',
    transfer_reason  VARCHAR(500) COMMENT '转移原因',
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '流转时间',
    INDEX idx_work_order_id (work_order_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单流转日志表';

-- 工单审计日志
CREATE TABLE IF NOT EXISTS work_order_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    work_order_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL COMMENT 'SUBMIT/AI_ANALYSIS/DISPATCH/STATUS_CHANGE/NOTE/COMPLETE/CANCEL',
    actor_type VARCHAR(16) NOT NULL COMMENT 'USER/SYSTEM/AI/AGENT',
    actor_id BIGINT NULL,
    action VARCHAR(128) NOT NULL,
    detail VARCHAR(512) NULL,
    internal_only TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1=仅客服可见',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_wo_id (work_order_id),
    INDEX idx_wo_user (work_order_id, internal_only)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单审计日志';

-- ============================================================
-- 知识库统一方案 — 新增表
-- ============================================================

CREATE TABLE IF NOT EXISTS knowledge_documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL COMMENT '文档标题',
    content LONGTEXT COMMENT '审核后的最终文本内容',
    toc_json TEXT COMMENT '预生成的TOC目录JSON',
    file_type VARCHAR(32) NOT NULL COMMENT '原始文件类型',
    original_file_url VARCHAR(512) COMMENT '原始文件存储路径',
    ocr_raw_json LONGTEXT COMMENT 'OCR原始结果存档',
    dify_document_id VARCHAR(64) COMMENT 'Dify中的文档ID',
    dify_sync_status VARCHAR(16) DEFAULT NULL COMMENT 'Dify同步状态',
    category VARCHAR(64) COMMENT '分类',
    tags VARCHAR(512) COMMENT '标签',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING_OCR' COMMENT 'PENDING_OCR/PENDING_REVIEW/PUBLISHING/PUBLISHED/ARCHIVED',
    version INT DEFAULT 1 COMMENT '当前版本号',
    is_latest TINYINT(1) DEFAULT 1 COMMENT '是否最新版本',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    preview_pdf_path VARCHAR(512) DEFAULT NULL COMMENT 'LibreOffice生成的PDF预览文件路径',
    published_at DATETIME COMMENT '发布时间',
    expired_at DATETIME COMMENT '过期时间',
    archived_at DATETIME COMMENT '归档时间',
    archive_reason VARCHAR(64) COMMENT '归档原因',
    reviewed_by VARCHAR(64) COMMENT '审核人',
    reviewed_at DATETIME COMMENT '审核提交时间',
    review_started_at DATETIME COMMENT '审核开始时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_category (category),
    INDEX idx_dify_doc (dify_document_id),
    INDEX idx_expired_at (expired_at),
    INDEX idx_is_latest (is_latest),
    FULLTEXT INDEX ft_content (content) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档主表';

CREATE TABLE IF NOT EXISTS knowledge_ocr_segments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL COMMENT '关联knowledge_documents.id',
    segment_index INT NOT NULL COMMENT '段落序号',
    ocr_text TEXT COMMENT 'OCR原始识别文字',
    reviewed_text TEXT COMMENT '人工修正后的文字',
    confidence DOUBLE COMMENT 'OCR置信度',
    bounding_box JSON COMMENT '区域坐标JSON',
    status VARCHAR(16) NOT NULL DEFAULT 'UNCERTAIN' COMMENT 'UNCERTAIN/CONFIRMED/REVIEWED/SKIPPED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_doc_id (document_id),
    INDEX idx_doc_status (document_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OCR分段审核记录';

CREATE TABLE IF NOT EXISTS knowledge_outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL COMMENT '关联knowledge_documents.id',
    event_type VARCHAR(32) NOT NULL COMMENT 'DIFY_UPLOAD/DIFY_UPDATE/DIFY_DELETE',
    payload TEXT NOT NULL COMMENT 'JSON事件载荷',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/COMPLETED/FAILED',
    retry_count INT DEFAULT 0,
    max_retry INT DEFAULT 5,
    last_error TEXT COMMENT '最后一次失败错误信息',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    next_retry_at DATETIME COMMENT '指数退避下次重试时间',
    INDEX idx_status_nextretry (status, next_retry_at),
    INDEX idx_doc_id (document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库双写投递出站表';

CREATE TABLE IF NOT EXISTS knowledge_revision_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL COMMENT '关联knowledge_documents.id',
    change_type VARCHAR(32) NOT NULL COMMENT 'CREATE/UPDATE/DELETE/PUBLISH/ARCHIVE',
    changed_fields JSON COMMENT '变更字段列表',
    old_value LONGTEXT COMMENT '变更前值',
    new_value LONGTEXT COMMENT '变更后值',
    changed_by VARCHAR(64) COMMENT '操作人',
    changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_doc_id (document_id),
    INDEX idx_changed_at (changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库变更日志';

CREATE TABLE IF NOT EXISTS knowledge_document_links (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_doc_id BIGINT NOT NULL COMMENT '引用方文档ID',
    target_doc_id BIGINT NOT NULL COMMENT '被引用文档ID',
    link_type VARCHAR(32) DEFAULT 'RELATED' COMMENT 'RELATED/DEPENDENCY/REFERENCE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_source (source_doc_id),
    INDEX idx_target (target_doc_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档间关联引用';

CREATE TABLE IF NOT EXISTS knowledge_read_status (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL COMMENT '关联knowledge_documents.id',
    reader_id BIGINT NOT NULL COMMENT '读者ID(客服ID)',
    read_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '阅读时间',
    UNIQUE KEY uk_doc_reader (document_id, reader_id),
    INDEX idx_reader (reader_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库已读状态';

CREATE TABLE IF NOT EXISTS knowledge_favorites (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL COMMENT '关联knowledge_documents.id',
    user_id BIGINT NOT NULL COMMENT '收藏者ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_doc_user (document_id, user_id),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库收藏';

CREATE TABLE IF NOT EXISTS knowledge_view_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL COMMENT '关联knowledge_documents.id',
    viewer_id BIGINT COMMENT '查看者ID',
    viewer_role VARCHAR(16) COMMENT 'ADMIN/AGENT',
    viewed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_doc_id (document_id),
    INDEX idx_viewed_at (viewed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库查看日志';

CREATE TABLE IF NOT EXISTS knowledge_search_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    keyword VARCHAR(255) NOT NULL,
    result_count INT DEFAULT 0 COMMENT '命中数量',
    searcher_id BIGINT COMMENT '搜索者ID',
    searched_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_searched_at` (searched_at),
  INDEX `idx_result_count` (result_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库搜索日志';


-- ===== 分类标签建表 =====
CREATE TABLE knowledge_categories (
    id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
    name        VARCHAR(64)  NOT NULL,
    creator_id  BIGINT       COMMENT '创建者，null=系统预置',
    sort_order  INT          DEFAULT 0 COMMENT '排序权重',
    icon        VARCHAR(128) COMMENT '图标',
    description VARCHAR(256) COMMENT '描述',
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_name (name)
);




-- ===== 客服技能标签表 =====
CREATE TABLE IF NOT EXISTS agent_skills (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id    BIGINT       NOT NULL COMMENT '客服用户ID',
    skill_name  VARCHAR(64)  NOT NULL COMMENT '技能标签：售前/售后/3C数码/服饰等',
    skill_level VARCHAR(16)  NOT NULL DEFAULT 'normal' COMMENT '技能等级：junior/normal/senior',
    is_active   TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_agent_skill (agent_id, skill_name),
    INDEX idx_skill (skill_name),
    INDEX idx_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服技能标签表';

INSERT INTO agent_skills (agent_id, skill_name, skill_level) VALUES
(4, '售前', 'senior'),
(4, '售后', 'senior'),
(4, '通用', 'senior')
ON DUPLICATE KEY UPDATE skill_level = VALUES(skill_level);

-- ===== SLA 时限配置表 =====
DROP TABLE IF EXISTS sla_config;
CREATE TABLE sla_config (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    biz_tag             VARCHAR(50)  NOT NULL COMMENT '业务标签 pre_sales/after_sales',
    priority            VARCHAR(20)  NOT NULL DEFAULT 'medium' COMMENT '优先级 high/medium/low',
    response_minutes    INT          NOT NULL COMMENT '响应时效（分钟），客服首次回复DDL',
    resolution_minutes  INT          NOT NULL COMMENT '解决时效（分钟），工单办结总时长',
    escalation_minutes  INT          NOT NULL COMMENT '未认领催办时限（分钟）',
    emergency_threshold DECIMAL(3,2) NOT NULL DEFAULT 0.25 COMMENT '紧急区阈值（剩余比例）',
    calendar_id         BIGINT       COMMENT '关联工作日历ID',
    is_active           TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_biz_priority (biz_tag, priority),
    INDEX idx_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SLA 时限配置表';

-- 默认SLA配置数据
INSERT INTO sla_config (biz_tag, priority, response_minutes, resolution_minutes, escalation_minutes) VALUES
('pre_sales',  'high',   10,  30,  5),
('pre_sales',  'medium', 20,  60,  8),
('pre_sales',  'low',    30,  120, 12),
('after_sales','high',   30,  240, 15),
('after_sales','medium', 60,  480, 20),
('after_sales','low',    120, 1440,30);

UPDATE sla_config SET calendar_id = 1 WHERE calendar_id IS NULL;


-- ============================================================
-- 服务时间日历管理
-- ============================================================

-- 工作日历表
DROP TABLE IF EXISTS sla_work_calendar;
CREATE TABLE sla_work_calendar (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    calendar_name       VARCHAR(100) NOT NULL COMMENT '日历名称',
    work_days           VARCHAR(50)  NOT NULL DEFAULT '1,2,3,4,5' COMMENT '工作日，逗号分隔 1=周一 7=周日',
    work_time_segments  JSON         NOT NULL COMMENT '工作时间段JSON',
    is_active           TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务时间日历表';

INSERT INTO sla_work_calendar (id, calendar_name, work_days, work_time_segments, is_active) VALUES
(1, '标准工作日历', '1,2,3,4,5',
 '[{"start":"09:00","end":"12:00"},{"start":"13:00","end":"18:00"}]', 1);

-- 工作日历特殊日期表
DROP TABLE IF EXISTS sla_calendar_special_date;
CREATE TABLE sla_calendar_special_date (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    calendar_id     BIGINT       NOT NULL COMMENT '关联日历ID',
    special_date    DATE         NOT NULL COMMENT '特殊日期',
    day_type        VARCHAR(20)  NOT NULL DEFAULT 'HOLIDAY' COMMENT '日期类型：HOLIDAY=全天休息, WORKDAY=调休工作日, PARTIAL=部分时段',
    work_segments   JSON         NULL COMMENT 'PARTIAL类型时的工作时段JSON',
    description     VARCHAR(200) COMMENT '描述',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_calendar_date (calendar_id, special_date),
    INDEX idx_calendar (calendar_id),
    INDEX idx_special_date (special_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作日历特殊日期表';

-- SLA暂停恢复日志表
DROP TABLE IF EXISTS sla_pause_log;
CREATE TABLE sla_pause_log (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    work_order_id               BIGINT       NOT NULL COMMENT '工单ID',
    pause_reason                VARCHAR(50)  NOT NULL COMMENT '暂停原因：CUSTOMER_WAITING/THIRD_PARTY/MANUAL_HOLD',
    resume_reason               VARCHAR(50)  NULL COMMENT '恢复原因',
    operator_id                 BIGINT       NULL COMMENT '操作人ID',
    pause_time                  DATETIME     NOT NULL COMMENT '暂停时间',
    resume_time                 DATETIME     NULL COMMENT '恢复时间（NULL=尚未恢复）',
    paused_effective_seconds    INT          NULL COMMENT '暂停期间的有效服务秒数',
    original_response_deadline  DATETIME     NULL COMMENT '暂停时的响应Deadline',
    original_sla_deadline       DATETIME     NULL COMMENT '暂停时的解决Deadline',
    resume_response_deadline    DATETIME     NULL COMMENT '恢复后的响应Deadline',
    resume_sla_deadline         DATETIME     NULL COMMENT '恢复后的解决Deadline',
    created_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_work_order_id (work_order_id),
    INDEX idx_pause_time (pause_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SLA暂停恢复日志表';


-- ============================================================
-- 客服与知识库管理员个人绩效统计
-- ============================================================

-- 客服每日绩效汇总表
CREATE TABLE IF NOT EXISTS agent_daily_stats (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id            BIGINT       NOT NULL COMMENT '客服用户ID',
    stat_date           DATE         NOT NULL COMMENT '统计日期',
    sessions_handled    INT          NOT NULL DEFAULT 0 COMMENT '当日接待会话数',
    avg_response_seconds DOUBLE      COMMENT '平均首次响应时间（秒）',
    satisfaction_avg    DECIMAL(3,2) COMMENT '当日平均满意度评分',
    sla_compliance_rate DECIMAL(5,2) COMMENT 'SLA达成率百分比',
    total_duration_min  INT          COMMENT '当日总服务时长（分钟）',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_agent_date (agent_id, stat_date),
    INDEX idx_stat_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服每日绩效汇总表';


-- ============================================================
-- RBAC 权限体系（执行前先手动 DROP 旧表）
 DROP TABLE IF EXISTS role_permissions;
 DROP TABLE IF EXISTS permissions;
-- ============================================================

CREATE TABLE IF NOT EXISTS permissions (
    id          BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '权限ID',
    code        VARCHAR(100) NOT NULL COMMENT '权限编码 {resource}:{action}',
    name        VARCHAR(50)  NOT NULL COMMENT '显示名称',
    resource    VARCHAR(50)  NOT NULL COMMENT '资源域 knowledge|user|role|order|work_order',
    action      VARCHAR(50)  NOT NULL COMMENT '操作 upload|read|delete|manage|create|update|review',
    description VARCHAR(200) COMMENT '权限描述',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id       BIGINT(20) NOT NULL COMMENT '角色ID',
    permission_id BIGINT(20) NOT NULL COMMENT '权限ID',
    PRIMARY KEY (role_id, permission_id),
    FOREIGN KEY (role_id)       REFERENCES roles (id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- ===== 初始化角色数据 =====
INSERT INTO roles (role_name, description) VALUES
('USER',   '普通注册用户'),
('VIP',    '会员用户'),
('ADMIN',  '系统管理员'),
('AGENT',  '普通客服'),
('KB_ADMIN', '知识库管理员');

-- ===== 初始化权限数据（10条纯管理侧权限） =====
INSERT INTO permissions (code, name, resource, action, description) VALUES
('knowledge:upload',   '知识库文档上传', 'knowledge', 'upload', '上传知识库文档'),
('knowledge:review',   '知识库文档审核', 'knowledge', 'review', '审核知识库文档'),
('knowledge:read',     '知识库文档查阅', 'knowledge', 'read',   '查阅知识库文档'),
('knowledge:delete',   '知识库文档删除', 'knowledge', 'delete', '删除知识库文档'),
('user:create',        '创建用户',       'user',      'create', '创建系统用户'),
('user:update',        '编辑用户',       'user',      'update', '编辑用户信息'),
('user:delete',        '删除用户',       'user',      'delete', '删除用户'),
('role:manage',        '角色权限管理',    'role',      'manage', '管理角色与权限分配'),
('order:read',         '查看订单',       'order',     'read',   '查看历史订单'),
('work_order:manage',  '工单管理',       'work_order','manage', '管理工单');

-- ===== 角色-权限关联（子查询驱动，不依赖自增ID） =====
-- ADMIN = 全部 10 项权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p WHERE r.role_name = 'ADMIN';

-- KB_ADMIN = knowledge:upload/review/read/delete（4项）
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.role_name = 'KB_ADMIN' AND p.resource = 'knowledge';

-- AGENT = work_order:manage + knowledge:read（2项，客服自带咨询/售后能力无需权限定义）
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.role_name = 'AGENT' AND p.code IN ('work_order:manage', 'knowledge:read');

-- USER / VIP 聊天权限由 Dify 侧 AI 控制，不在此处分配管理类权限

-- 工单评价字段

ALTER TABLE `work_orders` 
ADD COLUMN `rating` TINYINT(1) NULL DEFAULT NULL COMMENT '工单评价 1-5星，NULL=未评价' 
AFTER `exclude_from_sla`;

-- chat_messages 评价字段
ALTER TABLE `chat_messages`
ADD COLUMN `satisfaction` TINYINT(1) NULL DEFAULT NULL COMMENT '服务评价 1-5星，NULL=未评价，仅sender_type=AGENT写入'
AFTER `message_seq`;

-- knowledge_documents 添加审核开始字段
ALTER TABLE knowledge_documents 
ADD COLUMN review_started_at DATETIME COMMENT '审核开始时间' 
AFTER reviewed_at;