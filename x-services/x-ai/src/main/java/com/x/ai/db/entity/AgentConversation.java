package com.x.ai.db.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Accessors(chain = true)
@TableName(value = "agent_conversation", autoResultMap = true)
public class AgentConversation {

    /**
     * 会话 ID
     */
    @TableId("session_id")
    private String sessionId;

    /**
     * 用户 ID
     */
    @TableField("user_id")
    private String userId;

    /**
     * 标题
     */
    @TableField("title")
    private String title;

    /**
     * 额外关联的个人知识库 ID；空值或空数组表示仅使用个人公共知识库。
     */
    @TableField(value = "knowledge_base_ids", typeHandler = JacksonTypeHandler.class)
    private List<String> knowledgeBaseIds;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createTime;

    /**
     * 修改时间
     */
    @TableField(value = "update_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updateTime;
}
