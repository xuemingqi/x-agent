package com.x.ai.mcp.gateway.tool;

import com.x.ai.mcp.gateway.service.AgentInvocationService;
import com.x.api.ai.domain.ExternalAgentResponseDo;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * 对外提供 X Agent 能力的 MCP 工具。
 *
 * @author xuemingqi
 */
@Component
@RequiredArgsConstructor
public class AgentMcpTool {

    private final AgentInvocationService agentInvocationService;

    @McpTool(name = "run_agent", description = """
            委托 X Agent 执行通用智能体任务，并阻塞等待本轮最终结果。不限于知识问答：Agent 可自主拆解任务、使用子 Agent、
            加载 Skill、检索公共或个人知识库、操作沙箱文件、调用当前用户可用的 MCP 工具，以及管理 Agent 定时任务和个人 MCP 服务。
            具体可用的 Skill、知识和 MCP 能力取决于当前用户的配置；涉及创建、修改、删除、触发等操作时，Agent 会实际调用工具执行。
            支持通过 sessionId 连续多轮协作：首次调用可不传，后续相关请求应复用响应中的 sessionId；无关任务应使用新会话。
            """)
    public ExternalAgentResponseDo runAgent(@McpToolParam(description = """
                                                    交给 X Agent 的完整任务指令。请说明目标、必要上下文、约束和期望结果；可要求其问答、分析、
                                                    检索知识库、使用指定 Skill 或 MCP、处理文件、管理定时任务，或组合执行多步骤任务。
                                                    """) String question,
                                            @McpToolParam(required = false, description = """
                                                    会话 ID。首次调用或开始无关任务时不传；继续同一任务时，原样传入上次响应的 sessionId，
                                                    以保留上下文、记忆和任务连续性。
                                                    """) String sessionId) {
        return agentInvocationService.run(question, sessionId);
    }
}
