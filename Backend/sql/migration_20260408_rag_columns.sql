ALTER TABLE `knowledge_base`
  ADD COLUMN IF NOT EXISTS `rag_document_id` VARCHAR(64) NULL COMMENT 'rag-service 文档ID' AFTER `dify_document_id`,
  ADD COLUMN IF NOT EXISTS `vector_store` VARCHAR(32) NULL COMMENT '向量库类型' AFTER `rag_document_id`,
  ADD COLUMN IF NOT EXISTS `embedding_model` VARCHAR(64) NULL COMMENT '向量模型' AFTER `vector_store`,
  ADD COLUMN IF NOT EXISTS `index_status` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '索引状态：0待入库，1已入库，2失败，3已删除' AFTER `embedding_model`,
  ADD COLUMN IF NOT EXISTS `index_error` TEXT NULL COMMENT '索引错误信息' AFTER `index_status`;
