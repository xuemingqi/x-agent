-- 已有 MySQL 数据库执行一次；新库初始化已包含此字段。
ALTER TABLE agent_conversation_message
    ADD COLUMN token_usage JSON NULL COMMENT '本轮模型累计用量' AFTER tool_call_id;
