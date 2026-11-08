package com.x.ai.db.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.x.ai.db.enums.ConversationMessageRole;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("agent_conversation_message")
public class AgentConversationMessage {

    /**
     * 消息 ID
     */
    @TableId("id")
    private Long id;

    /**
     * 父消息 ID
     */
    @TableField("parent_id")
    private Long parentId;

    /**
     * 会话 ID
     */
    @TableField("session_id")
    private String sessionId;

    /**
     * 用户 ID
     */
    @TableField("user_id")
    private String userId;

    /**
     * 消息角色
     */
    @TableField("role")
    private ConversationMessageRole role;

    /**
     * 面向用户展示的消息内容
     */
    @TableField("content")
    private String content;

    /**
     * 工具调用 ID
     */
    @TableField("tool_call_id")
    private String toolCallId;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createTime;
}
