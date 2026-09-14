-- 已有数据库升级脚本：仅执行一次。
ALTER TABLE export_task
    ADD COLUMN retryCount int DEFAULT 0 NOT NULL COMMENT '已失败的执行次数' AFTER idempotentKey,
    ADD INDEX idx_status_updateTime (status, updateTime);
