-- ============================================================
-- SLA 有效时间计算 — 增量 DDL 迁移脚本
-- 执行环境：MySQL 8.0+
-- 说明：本脚本为增量迁移，不影响已有数据
-- ============================================================

-- ===== 1. 新增服务时间日历表 =====
CREATE TABLE IF NOT EXISTS sla_work_calendar (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    calendar_name       VARCHAR(100) NOT NULL COMMENT '日历名称',
    work_days           VARCHAR(50)  NOT NULL DEFAULT '1,2,3,4,5' COMMENT '工作日，逗号分隔 1=周一 7=周日',
    work_time_segments  JSON         NOT NULL COMMENT '工作时间段JSON，例：[{"start":"09:00","end":"12:00"},{"start":"13:00","end":"18:00"}]',
    is_active           TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务时间日历表';

-- ===== 2. 新增特殊日期表（节假日/调休日） =====
CREATE TABLE IF NOT EXISTS sla_calendar_special_date (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    calendar_id     BIGINT       NOT NULL COMMENT '关联日历ID',
    special_date    DATE         NOT NULL COMMENT '特殊日期',
    day_type        VARCHAR(20)  NOT NULL DEFAULT 'HOLIDAY' COMMENT '日期类型：HOLIDAY=全天休息, WORKDAY=调休工作日, PARTIAL=部分时段',
    work_segments   JSON         NULL COMMENT 'PARTIAL类型时的工作时段JSON',
    description     VARCHAR(200) COMMENT '描述，如"端午节"',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_calendar_date (calendar_id, special_date),
    INDEX idx_calendar (calendar_id),
    INDEX idx_special_date (special_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作日历特殊日期表';

-- ===== 3. 新增 SLA 暂停/恢复日志表 =====
CREATE TABLE IF NOT EXISTS sla_pause_log (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    work_order_id               BIGINT       NOT NULL COMMENT '工单ID',
    pause_reason                VARCHAR(50)  NOT NULL COMMENT '暂停原因：CUSTOMER_WAITING/THIRD_PARTY/MANUAL_HOLD',
    resume_reason               VARCHAR(50)  NULL COMMENT '恢复原因',
    operator_id                 BIGINT       NULL COMMENT '操作人ID（客服ID）',
    pause_time                  DATETIME     NOT NULL COMMENT '暂停时间',
    resume_time                 DATETIME     NULL COMMENT '恢复时间（NULL=尚未恢复）',
    paused_effective_seconds    INT          NULL COMMENT '暂停期间的有效服务秒数（恢复时计算）',
    original_response_deadline  DATETIME     NULL COMMENT '暂停时的响应Deadline',
    original_sla_deadline       DATETIME     NULL COMMENT '暂停时的解决Deadline',
    resume_response_deadline    DATETIME     NULL COMMENT '恢复后的响应Deadline',
    resume_sla_deadline         DATETIME     NULL COMMENT '恢复后的解决Deadline',
    created_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_work_order_id (work_order_id),
    INDEX idx_pause_time (pause_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SLA暂停恢复日志表';

-- ===== 4. work_orders 表新增列（使用 ALTER TABLE，对已有数据无破坏） =====
ALTER TABLE work_orders
    ADD COLUMN IF NOT EXISTS sla_paused              TINYINT(1)   NOT NULL DEFAULT 0 COMMENT 'SLA是否已暂停',
    ADD COLUMN IF NOT EXISTS effective_response_seconds  INT       NULL COMMENT '有效响应耗时（秒，扣除暂停和非工作时间）',
    ADD COLUMN IF NOT EXISTS effective_resolution_seconds INT      NULL COMMENT '有效解决耗时（秒，扣除暂停和非工作时间）',
    ADD COLUMN IF NOT EXISTS first_responder_id      BIGINT       NULL COMMENT '首次响应客服ID',
    ADD COLUMN IF NOT EXISTS resolver_id             BIGINT       NULL COMMENT '最终解决人ID',
    ADD COLUMN IF NOT EXISTS exclude_from_sla        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否排除SLA考核：1=排除';

-- ===== 5. sla_config 表新增列 =====
ALTER TABLE sla_config
    ADD COLUMN IF NOT EXISTS calendar_id BIGINT NULL COMMENT '关联工作日历ID';

-- ===== 6. 插入默认工作日历数据 =====
INSERT INTO sla_work_calendar (id, calendar_name, work_days, work_time_segments, is_active)
VALUES (1, '标准工作日历', '1,2,3,4,5',
        '[{"start":"09:00","end":"12:00"},{"start":"13:00","end":"18:00"}]', 1)
ON DUPLICATE KEY UPDATE calendar_name = VALUES(calendar_name);

-- 已有 sla_config 默认关联日历1
UPDATE sla_config SET calendar_id = 1 WHERE calendar_id IS NULL;
