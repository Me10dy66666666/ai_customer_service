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
  `user_type` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '用户类型：1-注册用户(普通)，2-会员用户，3-系统管理员',
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
  `user_type` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '用户类型：0-未注册用户，1-注册用户，2-会员，3-管理员',
  `user_input` TEXT NOT NULL COMMENT '用户输入',
  `ai_response` TEXT NOT NULL COMMENT 'AI回复',
  `dify_conversation_id` VARCHAR(100) COMMENT 'Dify会话ID',
  `intent` VARCHAR(100) COMMENT '识别的意图',
  `channel` VARCHAR(50) NOT NULL COMMENT '接入渠道：微信服务号，网页',
  `duration` INT COMMENT '对话时长（秒）',
  `satisfaction` TINYINT(1) COMMENT '满意度：1-非常满意，2-满意，3-一般，4-不满意',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_create_time` (`create_time`),
  INDEX `idx_channel` (`channel`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='咨询日志表';

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

CREATE TABLE IF NOT EXISTS `permissions` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '权限ID',
  `permission_name` VARCHAR(50) NOT NULL COMMENT '权限名称',
  `description` VARCHAR(200) COMMENT '权限描述',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission_name` (`permission_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

CREATE TABLE IF NOT EXISTS `role_permissions` (
  `role_id` BIGINT(20) NOT NULL COMMENT '角色ID',
  `permission_id` BIGINT(20) NOT NULL COMMENT '权限ID',
  PRIMARY KEY (`role_id`, `permission_id`),
  FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE,
  FOREIGN KEY (`permission_id`) REFERENCES `permissions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

CREATE TABLE IF NOT EXISTS `user_roles` (
  `user_id` BIGINT(20) NOT NULL COMMENT '用户ID',
  `role_id` BIGINT(20) NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`user_id`, `role_id`),
  FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- 初始化角色数据
INSERT INTO `roles` (`role_name`, `description`) VALUES 
('USER', '普通注册用户'),
('VIP', '会员用户'),
('ADMIN', '系统管理员');

INSERT INTO `permissions` (`permission_name`, `description`) VALUES 
('普通注册用户', '深度咨询'),
('会员用户', '深度咨询、售后服务、增值服务'),
('系统管理员 ', '管理知识库，查看订单日志，查看会话日志，分析用户行为画像');

INSERT INTO `role_permissions` (`role_id`, `permission_id`) VALUES 
(1, 1), -- USER 角色有普通注册用户权限
(2, 2), -- VIP 角色有会员用户权限
(3, 3); -- ADMIN 角色有系统管理员权限

-- 6.8 工单表（work_orders）
CREATE TABLE IF NOT EXISTS `work_orders` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '工单ID',
  `user_id` BIGINT(20) NOT NULL COMMENT '用户ID',
  `title` VARCHAR(200) NOT NULL COMMENT '标题',
  `description` TEXT NOT NULL COMMENT '描述',
  `type` VARCHAR(50) NOT NULL COMMENT '类型：售前/售后',
  `priority` VARCHAR(20) NOT NULL DEFAULT 'medium' COMMENT '优先级：high/medium/low',
  `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态：pending/processing/completed/cancelled',
  `handler_id` BIGINT(20) COMMENT '处理人ID',
  `result` TEXT COMMENT '处理结果',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_handler_id` (`handler_id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单表';
