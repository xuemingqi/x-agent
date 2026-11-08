package com.x.ai.service.impl;

import com.x.ai.common.constants.AgentConstant;
import com.x.ai.common.constants.PromptConstant;
import com.x.ai.common.domain.AgentResponseDo;
import com.x.ai.common.domain.ConversationFileDo;
import com.x.ai.common.domain.ConversationMessageDo;
import com.x.ai.common.dto.AgentRequestDto;
import com.x.ai.common.enums.AgentEventScope;
import com.x.ai.common.enums.AgentEventType;
import com.x.ai.common.model.BrowserScreenshotContext;
import com.x.ai.common.model.FileToolContext;
import com.x.ai.common.model.Registration;
import com.x.ai.common.model.PreparedAgentExecution;
import com.x.ai.common.model.ToolContext;
import com.x.ai.common.model.UserContext;
import com.x.ai.common.model.AiModelRuntimeConfig;
import com.x.ai.db.enums.ConversationMessageRole;
import com.x.framework.common.utils.IdUtil;
import com.x.framework.common.utils.StringUtil;
import com.x.ai.properties.AgentProperties;
import com.x.ai.properties.BrowserScreenshotProperties;
import com.x.ai.service.ActiveTaskRegistryService;
import com.x.ai.service.AgentExecutionService;
import com.x.ai.service.BrowserScreenshotService;
import com.x.ai.service.ConversationFileService;
import com.x.ai.service.ConversationService;
import com.x.ai.service.ConversationTurnRecordService;
import com.x.ai.service.HarnessAgentService;
import com.x.ai.service.AiModelRuntimeService;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.AgentStartEvent;
import io.agentscope.core.event.CustomEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentExecutionServiceImpl implements AgentExecutionService {

    private static final String DATA_MODEL_ID = "modelId";

    private static final String DATA_TEXT = "text";

    private static final String DATA_GENERATE_REASON = "generateReason";

    private static final String DATA_FILES = "files";

    private static final String DATA_DELTA = "delta";

    private static final String DATA_TOOL_CALL_ID = "toolCallId";

    private static final String DATA_TOOL_NAME = "toolName";

    private static final String DATA_STATE = "state";

    private final HarnessAgentService harnessAgentService;

    private final AiModelRuntimeService aiModelRuntimeService;

    private final ActiveTaskRegistryService activeTaskRegistryService;

    private final AgentProperties agentProperties;

    private final ConversationService conversationService;

    private final ConversationTurnRecordService conversationTurnRecordService;

    private final ConversationFileService conversationFileService;

    private final BrowserScreenshotService browserScreenshotService;

    private final BrowserScreenshotProperties browserScreenshotProperties;


    @Override
    public AgentResponseDo run(UserContext userContext, AgentRequestDto request) {
        PreparedAgentExecution execution = prepareConversationExecution(userContext, request, IdUtil.fastUUID());
        AgentResponseDo response = executeBlocking(execution, request);
        if (!StringUtil.isBlank(response.getText()) || response.getStructuredData().containsKey(DATA_FILES)) {
            RuntimeContext context = execution.getContext();
            conversationService.appendMessages(context.getUserId(), context.getSessionId(), execution.getMessageId(),
                    List.of(new ConversationMessageDo()
                            .setRole(ConversationMessageRole.ASSISTANT)
                            .setContent(response.getText())));
        }
        return response;
    }

    @Override
    public AgentResponseDo runStandalone(UserContext userContext, AgentRequestDto request) {
        PreparedAgentExecution execution = prepareExecution(userContext, request, null, List.of(), IdUtil.fastUUID());
        return executeBlocking(execution, request);
    }

    /**
     * 执行已准备好的同步任务并整理文本和产物。
     */
    private AgentResponseDo executeBlocking(PreparedAgentExecution execution, AgentRequestDto request) {
        RuntimeContext context = execution.getContext();
        Msg result = harnessAgentService.executeBlocking(execution.getAgent(), execution.getInput(), context)
                .block(agentProperties.getStreaming().getBlockingTimeout());
        conversationFileService.publishImages(context, execution.getMessageId(), result);
        String text = result == null ? "" : result.getTextContent();
        String generateReason = result == null || result.getGenerateReason() == null
                ? null : result.getGenerateReason().name();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(DATA_TEXT, text);
        if (generateReason != null) {
            data.put(DATA_GENERATE_REASON, generateReason);
        }
        List<ConversationFileDo> publishedFiles = conversationFileService.listPublishedOutputs(context.getUserId(),
                context.getSessionId(), execution.getMessageId());
        if (!CollectionUtils.isEmpty(publishedFiles)) {
            data.put(DATA_FILES, publishedFiles);
        }

        return buildResponse(AgentEventType.AGENT_RESULT, execution.getTaskId(), context, data)
                .setModelId(request.getModelId());
    }

    @Override
    public Flux<AgentResponseDo> streamRun(UserContext userContext, AgentRequestDto request) {
        return Flux.defer(() -> {
            String taskId = IdUtil.fastUUID();
            RuntimeContext context = RuntimeContext.builder()
                    .userId(userContext.getUserId()).sessionId(request.getSessionId()).build();
            AgentResponseDo heartbeat = buildResponse(AgentEventType.HEARTBEAT, taskId, context, Map.of());
            return Mono.fromCallable(() -> prepareConversationExecution(userContext, request, taskId))
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMapMany(execution -> streamPreparedExecution(execution, request))
                    .publish(shared -> Flux.merge(shared, Flux.interval(Duration.ofSeconds(
                                    browserScreenshotProperties.getHeartbeatIntervalSeconds()))
                            .map(tick -> heartbeat)
                            .takeUntilOther(shared.ignoreElements())))
                    // 首条心跳只表示连接已建立，不提前宣布尚未通过校验的任务开始执行。
                    .startWith(heartbeat)
                    .doFinally(signal -> browserScreenshotService.cancelTask(taskId));
        });
    }

    /**
     * 准备完成后才注册可中断任务，注册句柄覆盖流构造、执行、异常和取消的完整生命周期。
     */
    private Flux<AgentResponseDo> streamPreparedExecution(PreparedAgentExecution execution, AgentRequestDto request) {
        RuntimeContext context = execution.getContext();
        if (request.isBrowserScreenshotEnabled() && execution.isImageInput()) {
            context.put(BrowserScreenshotContext.class, new BrowserScreenshotContext()
                    .setTaskId(execution.getTaskId()).setMessageId(execution.getMessageId()));
        }
        return Flux.using(() -> activeTaskRegistryService.register(execution.getTaskId(), context.getUserId(),
                        () -> interruptExecution(execution)),
                registration -> {
                    Flux<AgentEvent> events = harnessAgentService.executeStream(execution.getAgent(), execution.getInput(), context);
                    Set<String> emittedFileIds = new LinkedHashSet<>();
                    return conversationTurnRecordService.record(context.getUserId(), context.getSessionId(),
                                    execution.getMessageId(), events)
                            .map(event -> mapEvent(execution.getTaskId(), context, execution.getMessageId(), emittedFileIds, event))
                            .startWith(started(execution.getTaskId(), request.getModelId(), context))
                            .concatWith(Flux.defer(() -> Flux.just(ended(execution.getTaskId(), context))))
                            .doOnCancel(() -> interruptExecution(execution));
                }, Registration::close);
    }

    /**
     * 主动中断或客户端断连时通知当前 Agent，并释放等待用户确认的截图请求。
     */
    private void interruptExecution(PreparedAgentExecution execution) {
        RuntimeContext context = execution.getContext();
        execution.getAgent().getDelegate().interrupt(context.getUserId(), context.getSessionId());
        browserScreenshotService.cancelTask(execution.getTaskId());
    }

    /**
     * 已完成授权与初始化后通知前端任务可以接受中断。
     */
    private AgentResponseDo started(String taskId, Long modelId, RuntimeContext context) {
        return buildResponse(AgentEventType.TASK_STARTED, taskId, context, Map.of(DATA_MODEL_ID, modelId));
    }

    /**
     * 执行与归档完成后通知前端本轮结束，允许继续处理队列。
     */
    private AgentResponseDo ended(String taskId, RuntimeContext context) {
        return buildResponse(AgentEventType.TASK_END, taskId, context, Map.of());
    }

    private AgentResponseDo mapEvent(String taskId, RuntimeContext context, Long userMessageId,
                                     Set<String> emittedFileIds, AgentEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        AgentEventType type;
        switch (event) {
            case CustomEvent custom when AgentEventType.BROWSER_SCREENSHOT_REQUESTED.getValue().equals(custom.getName()) -> {
                type = AgentEventType.BROWSER_SCREENSHOT_REQUESTED;
                data.putAll(custom.getValue());
            }
            case CustomEvent custom when AgentEventType.BROWSER_SCREENSHOT_COMPLETED.getValue().equals(custom.getName()) -> {
                type = AgentEventType.BROWSER_SCREENSHOT_COMPLETED;
                data.putAll(custom.getValue());
            }
            case AgentStartEvent ignored -> type = AgentEventType.AGENT_STARTED;
            case TextBlockDeltaEvent deltaEvent -> {
                type = AgentEventType.TEXT_DELTA;
                data.put(DATA_DELTA, deltaEvent.getDelta());
            }
            case ThinkingBlockDeltaEvent deltaEvent -> {
                type = AgentEventType.THINKING_BLOCK_DELTA;
                data.put(DATA_DELTA, deltaEvent.getDelta());
            }
            case ToolCallStartEvent toolEvent -> {
                type = AgentEventType.TOOL_CALL_START;
                putToolCall(data, toolEvent.getToolCallId(), toolEvent.getToolCallName());
            }
            case ToolCallEndEvent toolEvent -> {
                type = AgentEventType.TOOL_CALL_END;
                putToolCall(data, toolEvent.getToolCallId(), toolEvent.getToolCallName());
            }
            case ToolResultEndEvent toolEvent -> {
                type = AgentEventType.TOOL_RESULT_END;
                putToolCall(data, toolEvent.getToolCallId(), toolEvent.getToolCallName());
                data.put(DATA_STATE, String.valueOf(toolEvent.getState()));
                if (AgentConstant.CONVERSATION_FILE_PUBLISH_TOOL_NAME.equals(toolEvent.getToolCallName())) {
                    addPublishedFiles(data, context, userMessageId, emittedFileIds);
                }
            }
            case ToolResultTextDeltaEvent toolEvent -> {
                type = AgentEventType.TOOL_RESULT_TEXT_DELTA;
                putToolCall(data, toolEvent.getToolCallId(), toolEvent.getToolCallName());
                data.put(DATA_DELTA, toolEvent.getDelta());
            }
            case AgentResultEvent resultEvent -> {
                type = AgentEventType.AGENT_RESULT;
                Msg result = resultEvent.getResult();
                data.put(DATA_TEXT, result == null ? "" : result.getTextContent());
                data.put(DATA_GENERATE_REASON, result == null || result.getGenerateReason() == null
                        ? "" : result.getGenerateReason().name());
                if (StringUtil.isBlank(event.getSource())) {
                    conversationFileService.publishImages(context, userMessageId, result);
                    addPublishedFiles(data, context, userMessageId, emittedFileIds);
                }
            }
            case AgentEndEvent ignored -> type = AgentEventType.AGENT_END;
            default -> type = AgentEventType.UNKNOWN;
        }
        return buildResponse(type, taskId, context, data)
                .setScope(eventScope(event))
                .setSource(event.getSource());
    }

    private static AgentEventScope eventScope(AgentEvent event) {
        return StringUtil.isBlank(event.getSource()) ? AgentEventScope.MAIN : AgentEventScope.SUBAGENT;
    }

    private static void putToolCall(Map<String, Object> data, String toolCallId, String toolName) {
        data.put(DATA_TOOL_CALL_ID, toolCallId);
        data.put(DATA_TOOL_NAME, toolName);
    }

    /**
     * 查询本轮新发布的文件并避免在多个流式事件中重复推送。
     */
    private void addPublishedFiles(Map<String, Object> data, RuntimeContext context,
                                   Long userMessageId, Set<String> emittedFileIds) {
        List<ConversationFileDo> files = conversationFileService
                .listPublishedOutputs(context.getUserId(), context.getSessionId(), userMessageId)
                .stream()
                .filter(file -> emittedFileIds.add(file.getId()))
                .toList();
        if (!CollectionUtils.isEmpty(files)) {
            data.put(DATA_FILES, files);
        }
    }

    /**
     * 构建Agent 响应实体
     *
     * @param type    流式响应事件类型
     * @param taskId  任务id
     * @param context 运行实体
     * @param data    上下文
     * @return AgentResponseDo
     */
    private AgentResponseDo buildResponse(AgentEventType type, String taskId, RuntimeContext context, Map<String, Object> data) {
        AgentResponseDo response = new AgentResponseDo()
                .setEventType(type)
                .setScope(AgentEventScope.MAIN)
                .setTaskId(taskId)
                .setUserId(context.getUserId())
                .setSessionId(context.getSessionId())
                .setStructuredData(Map.copyOf(data));
        Object modelId = data.get(DATA_MODEL_ID);
        if (modelId instanceof Number number) {
            response.setModelId(number.longValue());
        }
        Object text = data.get(DATA_TEXT);
        if (text != null) {
            response.setText(String.valueOf(text));
        }
        Object generateReason = data.get(DATA_GENERATE_REASON);
        if (generateReason != null) {
            response.setGenerateReason(String.valueOf(generateReason));
        }
        return response;
    }

    /**
     * 将用户身份与本次请求携带的模型和业务属性分别写入 AgentScope 运行时上下文。
     */
    private RuntimeContext createRuntimeContext(String userId, AgentRequestDto request, Long userMessageId) {
        ToolContext toolContext = new ToolContext()
                .setUserId(userId)
                .setModelId(request.getModelId());
        FileToolContext fileToolContext = new FileToolContext()
                .setMessageId(userMessageId);
        return RuntimeContext.builder()
                .userId(userId)
                .sessionId(request.getSessionId())
                .put(ToolContext.class, toolContext)
                .put(FileToolContext.class, fileToolContext)
                .build();
    }

    /**
     * 记录用户消息并准备一次平台会话执行。
     */
    private PreparedAgentExecution prepareConversationExecution(UserContext userContext, AgentRequestDto request, String taskId) {
        String userId = userContext.getUserId();
        Long userMessageId = conversationService.appendUserMessage(userId, request.getSessionId(), request.getRequest());
        List<ConversationFileDo> attachments = conversationFileService.prepareInputs(userId, request.getSessionId(),
                request.getAttachmentIds(), userMessageId);
        return prepareExecution(userContext, request, userMessageId, attachments, taskId);
    }

    /**
     * 校验模型并准备一次同步或流式执行共享的上下文和输入。
     */
    private PreparedAgentExecution prepareExecution(UserContext userContext, AgentRequestDto request,
                                               Long userMessageId, List<ConversationFileDo> attachments, String taskId) {
        AiModelRuntimeConfig model = aiModelRuntimeService.requireRuntimeModel(userContext.getUserId(), request.getModelId());
        HarnessAgent agent = harnessAgentService.getAgent(model, userContext.getUserId());
        RuntimeContext context = createRuntimeContext(userContext.getUserId(), request, userMessageId);
        List<ContentBlock> content = new ArrayList<>();
        content.add(TextBlock.builder().text(buildAgentInput(request, attachments)).build());
        if (model.getMultimodalCapability().isImageInput()) {
            content.addAll(conversationFileService.readInputImages(userContext.getUserId(), request.getSessionId(), attachments));
        }
        UserMessage input = new UserMessage(content);
        return new PreparedAgentExecution()
                .setTaskId(taskId)
                .setMessageId(userMessageId)
                .setImageInput(model.getMultimodalCapability().isImageInput())
                .setAgent(agent)
                .setContext(context)
                .setInput(input);
    }

    /**
     * 把本轮附件的可信工作区路径附加到用户请求，避免模型猜测文件位置。
     */
    private String buildAgentInput(AgentRequestDto request, List<ConversationFileDo> attachments) {
        if (CollectionUtils.isEmpty(attachments)) {
            return request.getRequest();
        }
        String attachmentPaths = attachments.stream()
                .map(file -> StringUtil.format("- {}: {}", file.getName(), file.getWorkspacePath()))
                .collect(Collectors.joining("\n"));
        return StringUtil.format(PromptConstant.CONVERSATION_FILE_INPUT_PROMPT, request.getRequest(), attachmentPaths,
                AgentConstant.CONVERSATION_FILE_PUBLISH_TOOL_NAME);
    }

}
