package com.x.ai.service.impl;

import com.x.ai.common.domain.ConversationMessageDo;
import com.x.ai.db.enums.ConversationMessageRole;
import com.x.framework.common.utils.StringUtil;
import com.x.ai.service.ConversationService;
import com.x.ai.service.ConversationTurnRecordService;
import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.message.DataBlock;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConversationTurnRecordServiceImpl implements ConversationTurnRecordService {

    private static final String REASONING_MARKER = "[reasoning]\n";

    private final ConversationService conversationService;


    @Override
    public Flux<AgentEvent> record(String userId, String sessionId, Long userMessageId, Flux<AgentEvent> events) {
        return Flux.defer(() -> {
            ConversationTurnRecorder recorder = new ConversationTurnRecorder();
            return events.doOnNext(recorder::record)
                    .concatWith(Flux.defer(() -> {
                        conversationService.appendMessages(userId, sessionId, userMessageId, recorder.drainMessages());
                        return Flux.empty();
                    }));
        });
    }

    /**
     * 创建一条平台对话消息。
     */
    private static ConversationMessageDo message(ConversationMessageRole role, String content, String toolCallId) {
        return new ConversationMessageDo()
                .setRole(role)
                .setContent(content)
                .setToolCallId(toolCallId);
    }

    /**
     * 单次订阅使用的对话事件记录器，避免不同请求共享可变状态。
     */
    private static class ConversationTurnRecorder {

        private final List<ConversationMessageDo> messages = new ArrayList<>();

        private final StringBuilder pendingText = new StringBuilder();

        private final StringBuilder pendingReasoning = new StringBuilder();

        private final Map<String, StringBuilder> subagentReasoning = new LinkedHashMap<>();

        private final Map<String, String> toolNames = new LinkedHashMap<>();

        /**
         * 记录一个 Agent 流式事件。
         */
        private void record(AgentEvent event) {
            String source = event.getSource();
            switch (event) {
                case TextBlockDeltaEvent text -> recordText(source, text.getReplyId(), text.getDelta());
                case ThinkingBlockDeltaEvent thinking ->
                        recordReasoning(source, thinking.getReplyId(), thinking.getDelta());
                case ToolCallStartEvent tool -> recordToolCall(tool, source);
                case ToolResultEndEvent tool -> recordToolResult(tool);
                case AgentResultEvent result -> {
                    if (StringUtil.isBlank(source)) {
                        recordAgentResult(result.getResult());
                    }
                }
                case AgentEndEvent end -> recordAgentEnd(source, end.getReplyId());
                default -> {
                }
            }
        }

        /**
         * 分别记录主 Agent 文本和子 Agent 可见文本，避免子 Agent 事件污染主 Agent 状态。
         */
        private void recordText(String source, String replyId, String text) {
            if (StringUtil.isBlank(source)) {
                pendingText.append(text);
                return;
            }
            recordReasoning(source, replyId, text);
        }

        /**
         * 将思考增量写入对应执行者的缓冲区。
         */
        private void recordReasoning(String source, String replyId, String text) {
            if (StringUtil.isBlank(text)) {
                return;
            }
            if (StringUtil.isBlank(source)) {
                pendingReasoning.append(text);
                return;
            }
            subagentReasoning.computeIfAbsent(channelKey(replyId, source), ignored -> new StringBuilder()).append(text);
        }

        /**
         * 返回并清空本轮累计的可见消息。
         */
        private List<ConversationMessageDo> drainMessages() {
            recordPendingText();
            recordPendingSubagentReasoning();
            List<ConversationMessageDo> result = List.copyOf(messages);
            messages.clear();
            return result;
        }

        /**
         * 将工具调用及调用前的文本写入本轮消息。
         */
        private void recordToolCall(ToolCallStartEvent event, String source) {
            String content = reasoningBeforeTool(source, channelKey(event.getReplyId(), source));
            String marker = "[tool_call: " + event.getToolCallName() + "]";
            messages.add(message(ConversationMessageRole.ASSISTANT,
                    content.isEmpty() ? marker : REASONING_MARKER + content + "\n" + marker,
                    event.getToolCallId()));
            toolNames.put(event.getToolCallId(), event.getToolCallName());
        }

        /**
         * 取出工具调用前的思考内容，并把主 Agent 的待发送文本视为思考内容归档。
         */
        private String reasoningBeforeTool(String source, String channelKey) {
            if (StringUtil.isBlank(source)) {
                String content = joinContent(pendingReasoning, pendingText);
                pendingReasoning.setLength(0);
                pendingText.setLength(0);
                return content;
            }
            StringBuilder content = subagentReasoning.remove(channelKey);
            return content == null ? "" : content.toString().trim();
        }

        /**
         * 将工具执行结果写入本轮消息。
         */
        private void recordToolResult(ToolResultEndEvent event) {
            String toolName = toolNames.remove(event.getToolCallId());
            toolName = StringUtil.isBlank(toolName) ? event.getToolCallName() : toolName;
            String marker = "<tool_result state=\"" + event.getState() + "\" />";
            messages.add(message(ConversationMessageRole.TOOL, "[tool_result: " + toolName + "]\n" + marker, event.getToolCallId()));
        }

        /**
         * 记录无需继续调用工具的最终 Agent 结果。
         */
        private void recordAgentResult(Msg result) {
            if (result == null) {
                return;
            }
            if (result.getGenerateReason() == GenerateReason.TOOL_CALLS
                    || result.getGenerateReason() == GenerateReason.PERMISSION_ASKING
                    || result.getGenerateReason() == GenerateReason.INTERRUPTED) {
                return;
            }
            String content = result.getTextContent();
            if (content == null || content.isBlank()) {
                content = pendingText.toString();
            }
            recordPendingReasoning();
            if (!content.isBlank() || result.hasContentBlocks(DataBlock.class) || result.hasContentBlocks(ImageBlock.class)) {
                messages.add(message(ConversationMessageRole.ASSISTANT, content, null));
            }
            pendingText.setLength(0);
        }

        /**
         * 将尚未归档的文本追加为 Agent 消息。
         */
        private void recordPendingText() {
            recordPendingReasoning();
            if (pendingText.isEmpty()) {
                return;
            }
            messages.add(message(ConversationMessageRole.ASSISTANT, pendingText.toString(), null));
            pendingText.setLength(0);
        }

        /**
         * 在主 Agent 结束前保存尚未归档的思考内容。
         */
        private void recordPendingReasoning() {
            if (pendingReasoning.isEmpty()) {
                return;
            }
            messages.add(message(ConversationMessageRole.ASSISTANT,
                    REASONING_MARKER + pendingReasoning.toString(), null));
            pendingReasoning.setLength(0);
        }

        /**
         * 仅结束对应的子 Agent 记录，绝不清理主 Agent 的缓冲区。
         */
        private void recordAgentEnd(String source, String replyId) {
            if (StringUtil.isBlank(source)) {
                recordPendingText();
                return;
            }
            recordSubagentReasoning(channelKey(replyId, source));
        }

        /**
         * 兜底保存尚未收到结束事件的子 Agent 内容。
         */
        private void recordPendingSubagentReasoning() {
            for (String channelKey : List.copyOf(subagentReasoning.keySet())) {
                recordSubagentReasoning(channelKey);
            }
        }

        private void recordSubagentReasoning(String channelKey) {
            StringBuilder content = subagentReasoning.remove(channelKey);
            if (content == null || content.isEmpty()) {
                return;
            }
            messages.add(message(ConversationMessageRole.ASSISTANT, REASONING_MARKER + content, null));
        }

        private static String channelKey(String replyId, String source) {
            return StringUtil.isBlank(replyId) ? source : replyId;
        }

        private static String joinContent(StringBuilder first, StringBuilder second) {
            String firstContent = first.toString().trim();
            String secondContent = second.toString().trim();
            if (firstContent.isEmpty()) {
                return secondContent;
            }
            if (secondContent.isEmpty()) {
                return firstContent;
            }
            return firstContent + "\n" + secondContent;
        }

    }
}
