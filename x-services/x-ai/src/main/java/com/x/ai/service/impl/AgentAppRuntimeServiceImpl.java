package com.x.ai.service.impl;

import cn.hutool.core.lang.Snowflake;
import com.x.ai.service.AgentAppRuntimeService;
import com.x.ai.service.AgentAppService;
import com.x.ai.common.domain.AgentAppDetailDo;
import com.x.ai.common.domain.AgentAppGenerateDo;
import com.x.ai.common.domain.AgentAppRunDo;
import com.x.ai.common.domain.HttpExecutionDo;
import com.x.ai.common.dto.AgentAppDataSaveDto;
import com.x.ai.common.dto.AgentAppGenerateDto;
import com.x.ai.common.dto.AgentAppRunDto;
import com.x.ai.common.dto.AgentAppHttpRequestDto;
import com.x.ai.common.model.AiModelRuntimeConfig;
import com.x.ai.db.entity.AgentAppData;
import com.x.ai.db.service.AgentAppDataIService;
import com.x.ai.service.AiModelRuntimeService;
import com.x.ai.service.McpRuntimeService;
import com.x.ai.service.OutboundHttpService;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.IdUtil;
import com.x.framework.common.utils.JsonUtil;
import com.x.framework.common.utils.StringUtil;
import com.x.framework.common.utils.UserThreadLocalUtil;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAppRuntimeServiceImpl implements AgentAppRuntimeService {

    private static final String GENERATE_PERMISSION = "model.generate";

    private static final String RUN_PERMISSION = "agent.run";

    private static final String STORAGE_PERMISSION = "storage";

    private static final String HTTP_PERMISSION = "http.request";

    private static final Pattern DATA_KEY_PATTERN = Pattern.compile("[A-Za-z0-9_.-]{1,128}");

    private static final int MAX_DATA_BYTES = 1_048_576;

    private static final int RUN_MAX_ITERATIONS = 8;

    private static final Duration GENERATION_TIMEOUT = Duration.ofMinutes(2);

    private static final Duration MCP_REGISTRATION_TIMEOUT = Duration.ofSeconds(30);

    private final AgentAppService agentAppService;

    private final AiModelRuntimeService modelRuntimeService;

    private final McpRuntimeService mcpRuntimeService;

    private final AgentAppDataIService agentAppDataIService;

    private final Snowflake snowflake;

    private final OutboundHttpService outboundHttpService;


    @Override
    public Mono<HttpExecutionDo> requestHttp(String appId, AgentAppHttpRequestDto request) {
        requirePermission(UserThreadLocalUtil.getUserId(), appId, request.getVersion(), HTTP_PERMISSION);
        return outboundHttpService.execute(request.getRequest());
    }

    @Override
    public AgentAppGenerateDo generate(String appId, AgentAppGenerateDto request) {
        String userId = UserThreadLocalUtil.getUserId();
        requirePermission(userId, appId, request.getVersion(), GENERATE_PERMISSION);
        AiModelRuntimeConfig model = modelRuntimeService.requireRuntimeModel(userId, request.getModelId());
        String prompt = StringUtil.isBlank(request.getSystemPrompt())
                ? "根据用户输入完成本次文本生成任务。" : request.getSystemPrompt();
        RuntimeContext context = newContext(userId, appId);
        try (ReActAgent agent = ReActAgent.builder().name("app-" + appId).sysPrompt(prompt)
                .model(createModel(model)).toolkit(new Toolkit()).maxIters(1).build()) {
            Msg result = agent.call(request.getPrompt(), context).block(GENERATION_TIMEOUT);
            return new AgentAppGenerateDo().setText(requireText(result));
        } catch (RuntimeException error) {
            throw executionError(appId, error);
        }
    }

    @Override
    public AgentAppRunDo run(String appId, AgentAppRunDto request) {
        String userId = UserThreadLocalUtil.getUserId();
        requirePermission(userId, appId, request.getVersion(), RUN_PERMISSION);
        AiModelRuntimeConfig model = modelRuntimeService.requireRuntimeModel(userId, request.getModelId());
        Toolkit toolkit = new Toolkit();
        List<McpClientWrapper> clients = List.of();
        Set<McpClientWrapper> registered = new HashSet<>();
        Set<McpClientWrapper> released = new HashSet<>();
        RuntimeContext context = newContext(userId, appId);
        try {
            clients = mcpRuntimeService.getClients(mcpRuntimeService.load(userId));
            for (McpClientWrapper client : clients) {
                toolkit.registerMcpClient(client).doOnError(error -> released.add(client))
                        .block(MCP_REGISTRATION_TIMEOUT);
                registered.add(client);
            }
            try (ReActAgent agent = ReActAgent.builder().name("app-" + appId)
                    .sysPrompt("你是智能应用的一次性任务助手。根据用户请求使用当前可用的 MCP 工具完成任务，并返回结果。")
                    .model(createModel(model)).toolkit(toolkit).maxIters(RUN_MAX_ITERATIONS)
                    .permissionContext(PermissionContextState.builder().mode(PermissionMode.BYPASS).build())
                    .build()) {
                Msg result = agent.call(request.getRequest(), context).block(GENERATION_TIMEOUT);
                return new AgentAppRunDo().setText(requireText(result)).setSessionId(context.getSessionId());
            }
        } catch (RuntimeException error) {
            throw executionError(appId, error);
        } finally {
            toolkit.closeMcpClients();
            clients.stream().filter(client -> !registered.contains(client) && !released.contains(client))
                    .forEach(this::closeClient);
        }
    }

    @Override
    public JsonNode getData(String appId, String version, String key) {
        String userId = UserThreadLocalUtil.getUserId();
        requirePermission(userId, appId, version, STORAGE_PERMISSION);
        requireDataKey(key);
        AgentAppData data = agentAppDataIService.lambdaQuery().eq(AgentAppData::getOwnerUserId, userId)
                .eq(AgentAppData::getAppId, appId).eq(AgentAppData::getDataKey, key).one();
        return data == null ? null : JsonUtil.OBJECT_MAPPER.readTree(data.getValueJson());
    }

    @Override
    public boolean saveData(String appId, String version, String key, AgentAppDataSaveDto request) {
        String userId = UserThreadLocalUtil.getUserId();
        requirePermission(userId, appId, version, STORAGE_PERMISSION);
        requireDataKey(key);
        String value = JsonUtil.toJsonStrOrThrow(request.getValue());
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_DATA_BYTES) {
            throw new XException(ResponseCodeEnum.AGENT_APP_DATA_INVALID);
        }
        agentAppDataIService.upsert(new AgentAppData().setId(snowflake.nextId()).setOwnerUserId(userId)
                .setAppId(appId).setDataKey(key).setValueJson(value));
        return true;
    }

    @Override
    public boolean deleteData(String appId, String version, String key) {
        String userId = UserThreadLocalUtil.getUserId();
        requirePermission(userId, appId, version, STORAGE_PERMISSION);
        requireDataKey(key);
        agentAppDataIService.lambdaUpdate().eq(AgentAppData::getOwnerUserId, userId)
                .eq(AgentAppData::getAppId, appId).eq(AgentAppData::getDataKey, key).remove();
        return true;
    }

    /**
     * 每次请求重新核对归属、当前发布版本及能力，前端声明不能替代后端授权。
     */
    private void requirePermission(String userId, String appId, String version, String permission) {
        AgentAppDetailDo app = agentAppService.get(userId, appId);
        if (!Objects.equals(app.getCurrentVersion(), version)) {
            throw new XException(ResponseCodeEnum.AGENT_APP_VERSION_CONFLICT);
        }
        if (!app.getContent().getPermissions().contains(permission)) {
            throw new XException(ResponseCodeEnum.AGENT_APP_PERMISSION_DENIED);
        }
    }

    /**
     * 生成随机独立执行上下文，应用不能指定或恢复平台会话。
     */
    private RuntimeContext newContext(String userId, String appId) {
        return RuntimeContext.builder().userId(userId).sessionId("app:" + appId + ":" + IdUtil.fastUUID()).build();
    }

    /**
     * 模型凭证仅由已校验的个人配置提供，不进入 SDK 响应。
     */
    private Model createModel(AiModelRuntimeConfig configuration) {
        return OpenAIChatModel.builder().apiKey(configuration.getApiKey()).baseUrl(configuration.getBaseUrl())
                .modelName(configuration.getModelName()).contextWindowSize(configuration.getContextWindowSize())
                .stream(false).generateOptions(GenerateOptions.builder().temperature(configuration.getTemperature()).build())
                .build();
    }

    /**
     * 空返回视作执行失败，避免页面将未完成任务误报为成功。
     */
    private String requireText(Msg result) {
        if (result == null || StringUtil.isBlank(result.getTextContent())) {
            throw new XException(ResponseCodeEnum.AGENT_APP_EXECUTION_ERROR);
        }
        return result.getTextContent();
    }

    /**
     * 数据键采用固定 ASCII 字符集，保持 URL 路径和数据库大小写语义一致。
     */
    private void requireDataKey(String key) {
        if (key == null || key.equals(".") || key.equals("..") || !DATA_KEY_PATTERN.matcher(key).matches()) {
            throw new XException(ResponseCodeEnum.AGENT_APP_DATA_INVALID);
        }
    }

    /**
     * 不将模型网关和 MCP 错误中的原始响应、请求头或凭证回传到生成页面。
     */
    private XException executionError(String appId, RuntimeException error) {
        log.warn("智能应用执行失败，appId={}, errorType={}", appId, error.getClass().getSimpleName());
        return new XException(ResponseCodeEnum.AGENT_APP_EXECUTION_ERROR);
    }

    /**
     * 仅回收 SDK 尚未接管的连接；单个关闭失败不能阻止其它连接清理。
     */
    private void closeClient(McpClientWrapper client) {
        try {
            client.close();
        } catch (RuntimeException error) {
            log.warn("智能应用 MCP 连接关闭失败，name={}, errorType={}", client.getName(), error.getClass().getSimpleName());
        }
    }
}
