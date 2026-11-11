package com.x.ai.service.impl;

import com.x.ai.common.constants.AgentConstant;
import com.x.ai.common.constants.PromptConstant;
import com.x.ai.common.functional.BlockingInvocation;
import com.x.ai.common.functional.StreamingInvocation;
import com.x.ai.common.model.AgentRuntime;
import com.x.ai.common.model.AgentRuntimeConfiguration;
import com.x.ai.common.model.ResourceEnablement;
import com.x.ai.common.model.SkillRuntimeSnapshot;
import com.x.ai.common.model.AiModelRuntimeConfig;
import com.x.ai.converter.MultimodalOpenAIFormatter;
import com.x.ai.decorator.MultimodalOpenAITransportDecorator;
import com.x.framework.common.annotation.LogIgnore;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.JsonUtil;
import com.x.framework.common.utils.StringUtil;
import com.x.ai.properties.AgentProperties;
import com.x.ai.sandbox.docker.StreamingDockerSandboxClient;
import com.x.ai.service.AdministratorAuthorizationService;
import com.x.ai.service.AgentScopeStoreService;
import com.x.ai.service.HarnessAgentService;
import com.x.ai.service.McpRuntimeService;
import com.x.ai.service.PublicResourceVersionService;
import com.x.ai.service.ResourceEnablementService;
import com.x.ai.service.SkillRuntimeService;
import com.x.ai.skill.SkillRuntimeWorkspace;
import com.x.ai.tool.AgentScheduleTools;
import com.x.ai.tool.BrowserScreenshotTools;
import com.x.ai.tool.ConversationFileTools;
import com.x.ai.tool.KnowledgeLinkManagementTools;
import com.x.ai.tool.KnowledgeLinkTools;
import com.x.ai.tool.KnowledgeSearchTools;
import com.x.ai.tool.McpServerTools;
import com.x.ai.tool.PublicAdministrationTools;
import com.x.ai.tool.SkillManagementTools;
import com.x.ai.tool.SkillShareTools;
import com.x.ai.tool.middleware.ConversationFileMiddleware;
import com.x.ai.tool.middleware.InterruptResponseMiddleware;
import com.x.ai.tool.middleware.MultimodalResponseMiddleware;
import com.x.ai.tool.middleware.SubagentExecutionMiddleware;
import com.x.ai.tool.middleware.ToolAuthorizationMiddleware;
import com.x.ai.tool.middleware.TokenUsageMiddleware;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.transport.HttpTransportFactory;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.memory.MemoryConfig;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.memory.compaction.ToolResultEvictionConfig;
import io.agentscope.harness.agent.sandbox.WorkspaceSpec;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerFilesystemSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class HarnessAgentServiceImpl implements HarnessAgentService, AutoCloseable {

    private final AgentProperties agentProperties;

    private final AgentScopeStoreService agentScopeStoreService;

    private final ToolAuthorizationMiddleware toolAuthorizationMiddleware;

    private final ConversationFileMiddleware conversationFileMiddleware;

    private final MultimodalResponseMiddleware multimodalResponseMiddleware;

    private final InterruptResponseMiddleware interruptResponseMiddleware;

    private final SubagentExecutionMiddleware subagentExecutionMiddleware;

    private final ObjectProvider<AgentScheduleTools> agentScheduleTools;

    private final ObjectProvider<ConversationFileTools> conversationFileTools;

    private final ObjectProvider<BrowserScreenshotTools> browserScreenshotTools;

    private final ObjectProvider<KnowledgeLinkTools> knowledgeLinkTools;

    private final ObjectProvider<KnowledgeSearchTools> knowledgeSearchTools;

    private final TokenUsageMiddleware tokenUsageMiddleware;

    private final ObjectProvider<KnowledgeLinkManagementTools> knowledgeLinkManagementTools;

    private final ObjectProvider<McpServerTools> mcpServerTools;

    private final ObjectProvider<PublicAdministrationTools> publicAdministrationTools;

    private final ObjectProvider<SkillShareTools> skillShareTools;

    private final AdministratorAuthorizationService administratorAuthorizationService;

    private final SkillRuntimeService skillRuntimeService;

    private final ObjectProvider<SkillManagementTools> skillManagementTools;

    private final McpRuntimeService mcpRuntimeService;

    private final StreamingDockerSandboxClient streamingDockerSandboxClient;

    private final PublicResourceVersionService publicResourceVersionService;

    private final ResourceEnablementService resourceEnablementService;

    private final ConcurrentMap<String, ConcurrentMap<Long, AgentRuntime>> agentRuntimes = new ConcurrentHashMap<>();

    private final ConcurrentLinkedQueue<AgentRuntime> retiredAgents = new ConcurrentLinkedQueue<>();


    @Override
    @LogIgnore
    public HarnessAgent getAgent(AiModelRuntimeConfig model, String userId) {
        if (model == null || StringUtil.isBlank(userId)) {
            throw new XException(ResponseCodeEnum.AGENT_MODEL_UNAVAILABLE);
        }
        checkAgentCreationInterrupted();

        Long modelId = model.getModelId();
        ResourceEnablement enablement = resourceEnablementService.get(userId);
        SkillRuntimeSnapshot skills = skillRuntimeService.load(userId, enablement);
        AgentRuntimeConfiguration configuration = runtimeConfiguration(model, userId, enablement, skills);
        checkAgentCreationInterrupted();
        ConcurrentMap<Long, AgentRuntime> userRuntimes = agentRuntimes.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>());
        return userRuntimes.compute(modelId, (ignored, current)
                        -> current != null && current.getConfiguration().equals(configuration)
                        ? current : replaceAgent(current, model, userId, configuration, skills))
                .getAgent();
    }

    @Override
    public void evictUserAgents(String userId) {
        if (StringUtil.isBlank(userId)) {
            return;
        }
        Map<Long, AgentRuntime> runtimes = agentRuntimes.remove(userId);
        if (runtimes != null) {
            retiredAgents.addAll(runtimes.values());
        }
    }

    @Override
    public void evictAllAgents() {
        agentRuntimes.forEach((userId, userRuntimes) -> {
            if (agentRuntimes.remove(userId, userRuntimes)) {
                retiredAgents.addAll(userRuntimes.values());
            }
        });
    }

    @Override
    @LogIgnore
    public Mono<Msg> executeBlocking(HarnessAgent agent, Msg input, RuntimeContext context) {
        return blockingRound(input, context, agent::call, 0);
    }

    @Override
    @LogIgnore
    public Flux<AgentEvent> executeStream(HarnessAgent agent, Msg input, RuntimeContext context) {
        return streamingRound(input, context, agent::streamEvents, 0);
    }

    @Override
    public void close() {
        agentRuntimes.values().forEach(userRuntimes -> userRuntimes.values().forEach(AgentRuntime::shutdown));
        retiredAgents.forEach(AgentRuntime::shutdown);
        agentRuntimes.clear();
        retiredAgents.clear();
    }

    /**
     * 模型内容、实时角色和用户扩展快照共同决定缓存是否可复用。
     */
    private AgentRuntimeConfiguration runtimeConfiguration(AiModelRuntimeConfig model, String userId,
                                                           ResourceEnablement enablement, SkillRuntimeSnapshot skills) {
        return AgentRuntimeConfiguration.builder()
                .modelFingerprint(StringUtil.sha256Hex(JsonUtil.toJsonStrOrThrow(Arrays.asList(model.getBaseUrl(),
                        model.getModelName(), model.getApiKey(), model.getContextWindowSize(), model.getTemperature()))))
                .multimodalCapability(model.getMultimodalCapability())
                .administrator(resolveAdministrator(userId))
                .publicResourceVersion(publicResourceVersionService.currentVersion())
                .enablement(enablement)
                .skillFingerprint(skills.getFingerprint())
                .build();
    }

    /**
     * 构建配置变化时替换 Agent，旧实例保留到统一关闭以免中断正在执行的任务。
     */
    private AgentRuntime replaceAgent(AgentRuntime current, AiModelRuntimeConfig model, String userId,
                                      AgentRuntimeConfiguration configuration, SkillRuntimeSnapshot skills) {
        AgentRuntime replacement = createAgent(model, userId, configuration, skills);
        if (Thread.currentThread().isInterrupted()) {
            replacement.shutdown();
            throw new CancellationException("Agent initialization interrupted");
        }
        if (current != null) {
            retiredAgents.add(current);
        }
        return replacement;
    }

    /**
     * 用户服务临时不可用时只注册普通工具，公共写操作仍会单独校验实时权限。
     */
    private boolean resolveAdministrator(String userId) {
        try {
            return administratorAuthorizationService.isAdministrator(userId);
        } catch (XException error) {
            if (error.getResponseCodeEnum() != ResponseCodeEnum.ADMIN_AUTHORIZATION_UNAVAILABLE) {
                throw error;
            }
            log.warn("Administrator status unavailable, build regular user agent, userId={}", userId);
            return false;
        }
    }

    /**
     * 使用平台固定能力和当前用户可见的 MCP 构建独享 HarnessAgent。
     */
    private AgentRuntime createAgent(AiModelRuntimeConfig model, String userId,
                                     AgentRuntimeConfiguration configuration, SkillRuntimeSnapshot skills) {
        checkAgentCreationInterrupted();
        DistributedStore runtimeStore = agentScopeStoreService.createRuntimeStore();
        DockerFilesystemSpec filesystem = createFilesystem(createUserWorkspaceSpec(), runtimeStore);
        Toolkit toolkit = createToolkit(configuration.isAdministrator());
        List<McpClientWrapper> mcpClients = registerMcpClients(toolkit, userId, configuration.getEnablement());
        SkillRuntimeWorkspace workspace = null;
        try {
            checkAgentCreationInterrupted();
            workspace = SkillRuntimeWorkspace.create(agentProperties.getWorkspace(), skills.getRepositories());
            checkAgentCreationInterrupted();
            HarnessAgent agent = createHarnessAgent(model, runtimeStore, filesystem, toolkit,
                    skills, workspace.getDirectory());
            return new AgentRuntime(agent, mcpClients, configuration, workspace);
        } catch (IOException | RuntimeException error) {
            mcpClients.forEach(this::closeMcpClient);
            if (workspace != null) {
                workspace.close();
            }
            if (error instanceof RuntimeException runtimeError) {
                throw runtimeError;
            }
            throw new XException(ResponseCodeEnum.AGENT_WORKSPACE_STORAGE_ERROR);
        }
    }

    /**
     * 注册当前用户通用能力；公共管理工具仅授予实时管理员。
     */
    private Toolkit createToolkit(boolean administrator) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(conversationFileTools.getObject());
        toolkit.registerTool(browserScreenshotTools.getObject());
        toolkit.registerTool(knowledgeLinkTools.getObject());
        toolkit.registerTool(knowledgeSearchTools.getObject());
        toolkit.registerTool(agentScheduleTools.getObject());
        toolkit.registerTool(mcpServerTools.getObject());
        toolkit.registerTool(knowledgeLinkManagementTools.getObject());
        toolkit.registerTool(skillShareTools.getObject());
        toolkit.registerTool(skillManagementTools.getObject());
        if (administrator) {
            toolkit.registerTool(publicAdministrationTools.getObject());
        }
        return toolkit;
    }

    /**
     * 使用已注册工具和用户工作区构建 Agent，并应用对应入口的能力限制。
     */
    private HarnessAgent createHarnessAgent(AiModelRuntimeConfig model,
                                            DistributedStore runtimeStore, DockerFilesystemSpec filesystem, Toolkit toolkit,
                                            SkillRuntimeSnapshot skills, Path workspace) {
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name(agentProperties.getName())
                .agentId(agentProperties.getName())
                .sysPrompt(PromptConstant.DEFAULT_SYSTEM_PROMPT)
                .model(createModel(model))
                .workspace(workspace)
                .distributedStore(runtimeStore)
                .filesystem(filesystem)
                .toolkit(toolkit)
                .permissionContext(PermissionContextState.builder()
                        .mode(PermissionMode.BYPASS)
                        .build())
                .middleware(tokenUsageMiddleware)
                .middleware(toolAuthorizationMiddleware)
                .middleware(subagentExecutionMiddleware)
                .middleware(conversationFileMiddleware)
                .middleware(multimodalResponseMiddleware)
                .middleware(interruptResponseMiddleware)
                .compaction(createCompactionConfig(model.getContextWindowSize()))
                .toolResultEviction(ToolResultEvictionConfig.defaults())
                .disableDefaultWorkspaceSkills()
                .enablePendingToolRecovery(true);


        if (agentProperties.getMemory().isEnabled()) {
            builder.memory(createMemoryConfig());
        } else {
            builder.disableMemoryHooks();
        }

        return builder.skillRepositories(skills.getRepositories()).build();
    }

    /**
     * 全部注册完成后才保留成功客户端；中途终止时释放未保留连接，避免重复关闭 SDK 已回收的失败连接。
     */
    private List<McpClientWrapper> registerMcpClients(Toolkit toolkit, String userId, ResourceEnablement enablement) {
        List<McpClientWrapper> clients = mcpRuntimeService.getVisibleClients(userId, enablement);
        Set<McpClientWrapper> retainedClients = new HashSet<>();
        Set<McpClientWrapper> releasedClients = new HashSet<>();
        try {
            checkAgentCreationInterrupted();
            List<McpClientWrapper> registeredClients = clients.stream()
                    .filter(client -> tryRegisterMcpClient(toolkit, client, releasedClients))
                    .toList();
            retainedClients.addAll(registeredClients);
            return registeredClients;
        } finally {
            clients.stream()
                    .filter(client -> !retainedClients.contains(client) && !releasedClients.contains(client))
                    .forEach(this::closeMcpClient);
        }
    }

    /**
     * SDK 2.0.4 回收异步注册失败的连接；同步构造失败、未开始及成功后中断的连接仍由批次回收。
     */
    private boolean tryRegisterMcpClient(Toolkit toolkit, McpClientWrapper client, Set<McpClientWrapper> releasedClients) {
        checkAgentCreationInterrupted();
        try {
            toolkit.registerMcpClient(client)
                    .doOnError(error -> releasedClients.add(client))
                    .block();
            checkAgentCreationInterrupted();
            return true;
        } catch (RuntimeException error) {
            if (Thread.currentThread().isInterrupted()) {
                throw error;
            }
            log.warn("Skip unavailable MCP client, name={}, errorType={}", client.getName(), error.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * 已取消的请求不再构建或缓存 Agent，中断标志由调用链继续保留。
     */
    private void checkAgentCreationInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Agent initialization interrupted");
        }
    }

    /**
     * 文档知识通过 RAG 工具访问，沙箱只保留当前会话工作区。
     */
    private WorkspaceSpec createUserWorkspaceSpec() {
        WorkspaceSpec workspaceSpec = new WorkspaceSpec();
        workspaceSpec.setRoot(agentProperties.getSandbox().getWorkspaceRoot());
        workspaceSpec.setEntries(Map.of());
        return workspaceSpec;
    }

    /**
     * 使用统一的 Docker 配置创建文件系统，并显式带上分布式快照和执行锁。
     */
    private DockerFilesystemSpec createFilesystem(WorkspaceSpec workspaceSpec, DistributedStore runtimeStore) {
        AgentProperties.Sandbox sandbox = agentProperties.getSandbox();
        DockerFilesystemSpec filesystem = new DockerFilesystemSpec()
                .client(streamingDockerSandboxClient)
                .image(sandbox.getImage())
                .workspaceRoot(sandbox.getWorkspaceRoot())
                .memorySizeBytes(sandbox.getMemorySizeBytes())
                .cpuCount(sandbox.getCpuCount())
                .network(sandbox.getNetwork())
                .workspaceSpec(workspaceSpec);
        filesystem.isolationScope(IsolationScope.USER);
        filesystem.snapshotSpec(runtimeStore.sandboxSnapshotSpec());
        filesystem.executionGuard(runtimeStore.sandboxExecutionGuard());
        filesystem.workspaceProjectionRoots(List.of("AGENTS.md", "subagents", ".skills-cache"));
        return filesystem;
    }

    /**
     * 按模型上下文窗口计算压缩触发与保留 token 数。
     *
     * @param contextWindowSize 模型上下文窗口大小
     * @return AgentScope 压缩配置
     */
    private CompactionConfig createCompactionConfig(int contextWindowSize) {
        if (contextWindowSize <= 0) {
            throw new XException(ResponseCodeEnum.AGENT_CONFIG_INVALID);
        }
        AgentProperties.Compaction compaction = agentProperties.getCompaction();
        int triggerTokens = (int) Math.floor(contextWindowSize * compaction.getTriggerRatio());
        int keepTokens = (int) Math.floor(contextWindowSize * compaction.getKeepRatio());
        return CompactionConfig.builder()
                .triggerMessages(0)
                .triggerTokens(triggerTokens)
                .keepTokens(keepTokens)
                .flushBeforeCompact(agentProperties.getMemory().isEnabled())
                .offloadBeforeCompact(true)
                .build();
    }

    /**
     * 限制同一用户的记忆提取频率，并定期整理长期记忆。
     */
    private MemoryConfig createMemoryConfig() {
        AgentProperties.Memory memory = agentProperties.getMemory();
        return MemoryConfig.builder()
                .flushTrigger(MemoryConfig.FlushTrigger.throttled(memory.getFlushMinGap()))
                .consolidationMinGap(memory.getConsolidationMinGap())
                .build();
    }

    /**
     * 根据数据库模型配置创建 AgentScope Model。
     */
    private Model createModel(AiModelRuntimeConfig model) {
        GenerateOptions.Builder options = GenerateOptions.builder();
        if (model.getMultimodalCapability().isImageOutput()) {
            options.additionalBodyParam("modalities", List.of("text", "image"));
        }
        return OpenAIChatModel.builder()
                .apiKey(model.getApiKey())
                .baseUrl(model.getBaseUrl())
                .modelName(model.getModelName())
                .stream(true)
                .formatter(new MultimodalOpenAIFormatter(model.getMultimodalCapability().isImageInput()))
                .httpTransport(new MultimodalOpenAITransportDecorator(HttpTransportFactory.getDefault()))
                .generateOptions(options
                        .temperature(model.getTemperature())
                        .build())
                .contextWindowSize(model.getContextWindowSize())
                .build();
    }

    /**
     * 递归推进阻塞调用，并限制连续自动批准次数。
     */
    private Mono<Msg> blockingRound(Msg input, RuntimeContext context, BlockingInvocation invocation, int approvalCount) {
        return invocation.call(input, context).flatMap(result -> {
            if (result.getGenerateReason() != GenerateReason.PERMISSION_ASKING) {
                return Mono.justOrEmpty(result);
            }
            List<ToolUseBlock> pending = result.getContentBlocks(ToolUseBlock.class);
            return continueAfterApproval(pending, approvalCount,
                    approval -> blockingRound(approval, context, invocation, approvalCount + 1));
        });
    }

    /**
     * 收集待确认工具调用，并在当前流结束后自动续接批准后的事件流。
     */
    private Flux<AgentEvent> streamingRound(Msg input, RuntimeContext context, StreamingInvocation invocation, int approvalCount) {
        int maxConsecutiveApprovals = agentProperties.getApproval().getMaxConsecutiveApprovals();
        return Flux.defer(() -> {
            List<ToolUseBlock> pending = new ArrayList<>();
            Flux<AgentEvent> visible = invocation.stream(input, context)
                    .handle((event, sink) -> {
                        if (event instanceof RequireUserConfirmEvent confirm) {
                            pending.addAll(confirm.getToolCalls());
                        } else if (event instanceof AgentResultEvent result && result.getResult() != null
                                && result.getResult().getGenerateReason() == GenerateReason.PERMISSION_ASKING) {
                            if (CollectionUtils.isEmpty(pending)) {
                                pending.addAll(result.getResult().getContentBlocks(ToolUseBlock.class));
                            }
                        } else if (!(event instanceof AgentEndEvent) || CollectionUtils.isEmpty(pending)) {
                            sink.next(event);
                        }
                    });
            return visible.concatWith(Flux.defer(() -> {
                if (CollectionUtils.isEmpty(pending)) {
                    return Flux.empty();
                }
                if (approvalCount >= maxConsecutiveApprovals) {
                    return Flux.error(new XException(ResponseCodeEnum.AGENT_RUNTIME_INVALID));
                }
                return streamingRound(approvalMessage(pending), context, invocation, approvalCount + 1);
            }));
        });
    }

    /**
     * 校验待批准调用和连续批准上限后执行阻塞续接逻辑。
     */
    private <T> Mono<T> continueAfterApproval(List<ToolUseBlock> pending, int approvalCount, Function<Msg, Mono<T>> continuation) {
        if (CollectionUtils.isEmpty(pending)) {
            return Mono.error(new XException(ResponseCodeEnum.AGENT_RUNTIME_INVALID));
        }
        if (approvalCount >= agentProperties.getApproval().getMaxConsecutiveApprovals()) {
            return Mono.error(new XException(ResponseCodeEnum.AGENT_RUNTIME_INVALID));
        }
        return continuation.apply(approvalMessage(pending));
    }

    /**
     * 将待确认工具调用转换为 AgentScope 批准消息。
     */
    private Msg approvalMessage(List<ToolUseBlock> pending) {
        List<ConfirmResult> results = pending.stream()
                .map(toolCall -> new ConfirmResult(true, toolCall))
                .toList();
        return UserMessage.builder()
                .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, results))
                .build();
    }

    /**
     * 关闭 AgentScope 不会自动关闭的 MCP 客户端。
     */
    private void closeMcpClient(McpClientWrapper client) {
        try {
            client.close();
        } catch (RuntimeException error) {
            log.warn("Failed to close MCP client, name={}", client.getName(), error);
        }
    }

}
