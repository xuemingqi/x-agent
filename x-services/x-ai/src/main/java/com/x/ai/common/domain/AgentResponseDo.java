package com.x.ai.common.domain;

import com.x.ai.common.enums.AgentEventScope;
import com.x.ai.common.enums.AgentEventType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.util.Map;

/**
 * @author : xuemingqi
 */
@Data
@Accessors(chain = true)
@Schema(name = "AgentResponseDo", description = "Agent 响应实体")
public class AgentResponseDo {

    @Schema(name = "eventType", description = "流式事件类型；同步执行时为 agent_result")
    private AgentEventType eventType;

    @Schema(name = "scope", description = "事件所属层级：main 或 subagent")
    private AgentEventScope scope;

    @Schema(name = "source", description = "AgentScope 子 Agent 来源路径；主 Agent 事件为空")
    private String source;

    @Schema(name = "taskId", description = "任务id")
    private String taskId;

    @Schema(name = "userId", description = "用户id")
    private String userId;

    @Schema(name = "sessionId", description = "会话id")
    private String sessionId;

    @Schema(name = "modelId", description = "实际使用的模型配置id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long modelId;

    @Schema(name = "generateReason", description = "AgentScope 生成结束原因")
    private String generateReason;

    @Schema(name = "text", description = "文本结果")
    private String text;

    @Schema(name = "structuredData", description = "事件扩展数据")
    private Map<String, Object> structuredData;
}
