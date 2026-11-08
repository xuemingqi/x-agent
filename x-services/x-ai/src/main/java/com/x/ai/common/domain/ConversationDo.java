package com.x.ai.common.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
@Schema(name = "ConversationDo", description = "用户对话")
public class ConversationDo {

    @Schema(name = "sessionId", description = "会话 ID")
    private String sessionId;

    @Schema(name = "title", description = "首条用户消息")
    private String title;

    @Schema(name = "createdAt", description = "对话创建时间")
    private String createdAt;

    @Schema(name = "updatedAt", description = "对话更新时间")
    private String updatedAt;

    @Schema(name = "messages", description = "按 JSONL 顺序排列的非系统消息")
    private List<ConversationMessageDo> messages;

    @Schema(name = "files", description = "会话中上传和生成的文件")
    private List<ConversationFileDo> files;
}
