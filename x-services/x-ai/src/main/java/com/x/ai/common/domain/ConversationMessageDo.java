package com.x.ai.common.domain;

import com.x.ai.db.enums.ConversationMessageRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@Schema(name = "ConversationMessageDo", description = "对话消息")
public class ConversationMessageDo {

    @Schema(name = "id", description = "消息 ID")
    private String id;

    @Schema(name = "parentId", description = "父消息 ID")
    private String parentId;

    @Schema(name = "role", description = "消息角色")
    private ConversationMessageRole role;

    @Schema(name = "content", description = "面向用户展示的消息内容")
    private String content;

    @Schema(name = "toolCallId", description = "工具调用 ID")
    private String toolCallId;

    /**
     * 本轮主 Agent 和子 Agent 的累计模型用量，历史消息可为空。
     */
    @Schema(name = "usage", description = "本轮主 Agent 和子 Agent 的累计模型用量，历史消息可为空")
    private TokenUsageDo usage;

    @Schema(name = "createdAt", description = "消息创建时间")
    private String createdAt;
}
