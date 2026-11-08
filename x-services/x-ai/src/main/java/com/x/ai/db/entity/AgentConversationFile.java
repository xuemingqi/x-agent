package com.x.ai.db.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.x.ai.db.enums.AgentConversationFileKind;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("agent_conversation_file")
public class AgentConversationFile {

    /**
     * 文件 ID。
     */
    @TableId("id")
    private Long id;

    /**
     * 文件所属的用户消息 ID，上传后未发送时为空。
     */
    @TableField("message_id")
    private Long messageId;

    /**
     * 会话 ID。
     */
    @TableField("session_id")
    private String sessionId;

    /**
     * 所属用户 ID。
     */
    @TableField("user_id")
    private String userId;

    /**
     * 文件类型：1 用户附件，2 Agent 产物。
     */
    @TableField("kind")
    private AgentConversationFileKind kind;

    /**
     * 展示和下载使用的文件名。
     */
    @TableField("file_name")
    private String fileName;

    /**
     * S3 对象 Key。
     */
    @TableField("object_key")
    private String objectKey;

    /**
     * AgentScope 工作区相对路径。
     */
    @TableField("sandbox_path")
    private String sandboxPath;

    /**
     * 文件媒体类型。
     */
    @TableField("content_type")
    private String contentType;

    /**
     * 文件字节数。
     */
    @TableField("size_bytes")
    private Long sizeBytes;

    /**
     * 创建时间。
     */
    @TableField(value = "create_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createTime;
}
