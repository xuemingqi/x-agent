package com.x.ai.tool;

import com.x.ai.service.ChatService;
import com.x.api.ai.domain.ExternalAgentResponseDo;
import com.x.api.ai.dto.ExternalAgentRequestDto;
import com.x.framework.common.constants.CommonConstant;
import com.x.framework.common.dto.UserInfo;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XBaseException;
import com.x.framework.common.response.BaseResult;
import com.x.framework.common.utils.JsonUtil;
import com.x.framework.common.utils.UserThreadLocalUtil;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * MCP 与 HTTP Open API 共用本地 Agent 执行逻辑和 API Key 身份。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalAgentMcpTool {

    private final ChatService chatService;

    private final Validator validator;

    /**
     * 定义对外工具契约，协议请求之间通过业务 sessionId 延续 Agent 对话。
     */
    public McpStatelessServerFeatures.SyncToolSpecification specification() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("run_agent")
                .description("""
                        委托 X Agent 执行通用智能体任务，并等待本轮最终结果。Agent 可拆解任务、使用子 Agent、
                        加载 Skill、检索知识库、操作沙箱文件、调用当前用户可用的 MCP 工具，以及管理定时任务和个人 MCP。
                        具体能力取决于当前用户配置；创建、修改、删除等操作会实际执行。
                        首次调用可不传 sessionId，后续相关请求应复用响应中的 sessionId；无关任务使用新会话。
                        """)
                .inputSchema(new McpSchema.JsonSchema("object", Map.of(
                        "question", Map.of("type", "string", "minLength", 1, "description", "完整任务指令、上下文和约束"),
                        "sessionId", Map.of("type", "string", "maxLength", 64, "description", "继续同一任务时复用会话 ID")),
                        List.of("question"), false, null, null))
                .build();
        return McpStatelessServerFeatures.SyncToolSpecification.builder().tool(tool).callHandler(this::run).build();
    }

    /**
     * 协议调度可能切换线程，显式绑定可信身份，并在调用后恢复线程状态。
     */
    private McpSchema.CallToolResult run(McpTransportContext context, McpSchema.CallToolRequest call) {
        UserInfo previous = UserThreadLocalUtil.get();
        try {
            UserThreadLocalUtil.set(UserInfo.builder().userId((String) context.get(CommonConstant.USER_ID)).build());
            Map<String, Object> arguments = call.arguments();
            ExternalAgentRequestDto request = new ExternalAgentRequestDto()
                    .setQuestion((String) arguments.get("question"))
                    .setSessionId((String) arguments.get("sessionId"));
            if (!validator.validate(request).isEmpty()) {
                return failure("请提供有效的任务内容和会话 ID");
            }
            BaseResult<ExternalAgentResponseDo> result = chatService.runWithDefaultModel(request);
            if (result == null || result.getCode() != ResponseCodeEnum.SUCCESS.getCode() || result.getData() == null) {
                return failure(ResponseCodeEnum.AGENT_EXECUTION_ERROR.getMsg());
            }
            return McpSchema.CallToolResult.builder()
                    .addTextContent(JsonUtil.toJsonStrOrThrow(result.getData()))
                    .structuredContent(result.getData())
                    .isError(false)
                    .build();
        } catch (XBaseException error) {
            return failure(error.getResponseCodeEnum().getMsg());
        } catch (RuntimeException error) {
            log.warn("External MCP execution failed, errorType={}", error.getClass().getSimpleName());
            return failure(ResponseCodeEnum.AGENT_EXECUTION_ERROR.getMsg());
        } finally {
            if (previous == null) {
                UserThreadLocalUtil.clear();
            } else {
                UserThreadLocalUtil.set(previous);
            }
        }
    }

    /**
     * 仅向协议客户端返回稳定业务错误，避免携带内部请求或凭证。
     */
    private McpSchema.CallToolResult failure(String message) {
        return McpSchema.CallToolResult.builder().addTextContent(message).isError(true).build();
    }
}
