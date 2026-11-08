package com.x.ai.common.domain;

import com.x.ai.db.enums.AgentConversationFileKind;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@Schema(name = "ConversationFileDo", description = "会话文件")
public class ConversationFileDo {

    @Schema(name = "id", description = "文件 ID")
    private String id;

    @Schema(name = "sessionId", description = "所属会话 ID")
    private String sessionId;

    @Schema(name = "messageId", description = "文件所属的用户消息 ID")
    private String messageId;

    @Schema(name = "kind", description = "文件类型：1-用户附件，2-Agent 产物")
    private AgentConversationFileKind kind;

    @Schema(name = "name", description = "展示和下载使用的文件名")
    private String name;

    @Schema(name = "contentType", description = "文件媒体类型")
    private String contentType;

    @Schema(name = "sizeBytes", description = "文件字节数")
    private long sizeBytes;

    @Schema(name = "workspacePath", description = "AgentScope 工作区相对路径")
    private String workspacePath;

    @Schema(name = "createdAt", description = "文件创建时间")
    private String createdAt;
}
