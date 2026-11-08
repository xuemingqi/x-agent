package com.x.ai.service.impl;

import com.x.framework.common.utils.UserThreadLocalUtil;
import com.x.ai.common.domain.ConversationFileDo;
import com.x.ai.common.dto.BrowserScreenshotResultDto;
import com.x.ai.common.enums.AgentEventType;
import com.x.ai.common.enums.BrowserScreenshotStatus;
import com.x.ai.common.model.BrowserScreenshotContext;
import com.x.ai.common.model.PendingBrowserScreenshot;
import com.x.ai.db.enums.AgentConversationFileKind;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.IdUtil;
import com.x.ai.properties.BrowserScreenshotProperties;
import com.x.ai.service.BrowserScreenshotService;
import com.x.ai.service.ConversationFileService;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEventEmitter;
import io.agentscope.core.event.CustomEvent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
public class BrowserScreenshotServiceImpl implements BrowserScreenshotService {

    private final ConversationFileService conversationFileService;

    private final BrowserScreenshotProperties properties;

    private final ConcurrentMap<String, PendingBrowserScreenshot> requests = new ConcurrentHashMap<>();


    @Override
    public Mono<ToolResultBlock> request(String fileId, String reason, RuntimeContext context) {
        return Mono.deferContextual(reactorContext -> {
            BrowserScreenshotContext browser = context.get(BrowserScreenshotContext.class);
            AgentEventEmitter emitter = AgentEventEmitter.fromContext(reactorContext).orElse(null);
            if (browser == null || emitter == null) {
                return Mono.just(ToolResultBlock.text("当前入口或模型不支持浏览器截图。请交付 HTML 文件，不要声称已看过运行效果。"));
            }
            if (reason == null || reason.isBlank() || reason.length() > properties.getMaxReasonLength()) {
                return Mono.just(ToolResultBlock.error("截图检查目的不能为空，且不能超过 " + properties.getMaxReasonLength() + " 字。"));
            }
            ConversationFileDo preview = conversationFileService.listPublishedOutputs(context.getUserId(),
                            context.getSessionId(), browser.getMessageId()).stream()
                    .filter(file -> Objects.equals(file.getId(), fileId) && isHtml(file))
                    .findFirst().orElse(null);
            if (preview == null) {
                return Mono.just(ToolResultBlock.error("请先发布本轮生成的单文件 HTML，再使用返回的文件 ID 请求截图。"));
            }
            PendingBrowserScreenshot pending = new PendingBrowserScreenshot()
                    .setRequestId(IdUtil.fastUUID()).setTaskId(browser.getTaskId())
                    .setUserId(context.getUserId()).setSessionId(context.getSessionId())
                    .setMessageId(browser.getMessageId()).setEmitter(emitter)
                    .setExpiresAt(System.currentTimeMillis() + Duration.ofSeconds(properties.getRequestTimeoutSeconds()).toMillis());
            requests.put(pending.getRequestId(), pending);
            emitter.emit(new CustomEvent(AgentEventType.BROWSER_SCREENSHOT_REQUESTED.getValue(), Map.of(
                    "requestId", pending.getRequestId(), "reason", reason, "previewFile", preview,
                    "expiresAt", pending.getExpiresAt())));
            return pending.getResult().asMono()
                    .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                    .onErrorResume(TimeoutException.class, error -> {
                        finish(pending, BrowserScreenshotStatus.EXPIRED, "用户未在有效时间内完成截图，请勿再次自动请求。", List.of(), List.of());
                        return pending.getResult().asMono();
                    })
                    .doFinally(signal -> {
                        synchronized (pending) {
                            pending.setCompleted(true);
                            requests.remove(pending.getRequestId(), pending);
                        }
                    });
        });
    }

    @Override
    public void complete(String taskId, String requestId, BrowserScreenshotResultDto result) {
        PendingBrowserScreenshot pending = requests.get(requestId);
        String userId = UserThreadLocalUtil.getUserId();
        if (pending == null || !Objects.equals(pending.getUserId(), userId)
                || !Objects.equals(pending.getTaskId(), taskId)) {
            throw new XException(ResponseCodeEnum.AGENT_RUNTIME_INVALID);
        }
        synchronized (pending) {
            if (pending.isCompleted() || System.currentTimeMillis() >= pending.getExpiresAt()) {
                throw new XException(ResponseCodeEnum.AGENT_RUNTIME_INVALID);
            }
            if (!Boolean.TRUE.equals(result.getAccepted())) {
                finish(pending, BrowserScreenshotStatus.DECLINED, "用户拒绝或取消了截图。请继续处理任务，不要再次自动请求截图。", List.of(), List.of());
                return;
            }
            if (result.getFileId() == null) {
                throw new XException(ResponseCodeEnum.AGENT_CONVERSATION_FILE_INVALID);
            }
            ConversationFileDo file = conversationFileService.getFile(userId, pending.getSessionId(), result.getFileId());
            if (file.getKind() != AgentConversationFileKind.INPUT || file.getMessageId() != null
                    || !"image/png".equals(file.getContentType()) || !file.getName().endsWith(".png")) {
                throw new XException(ResponseCodeEnum.AGENT_CONVERSATION_FILE_INVALID);
            }
            List<ConversationFileDo> bound = conversationFileService.prepareInputs(userId, pending.getSessionId(),
                    List.of(result.getFileId()), pending.getMessageId());
            List<ContentBlock> images = conversationFileService.readInputImages(userId, pending.getSessionId(), bound);
            if (images.size() != 1) {
                throw new XException(ResponseCodeEnum.AGENT_CONVERSATION_FILE_INVALID);
            }
            finish(pending, BrowserScreenshotStatus.RECEIVED, "用户授权截取的浏览器画面如下。请根据实际图片检查 HTML 效果；"
                    + "用户选择的画面可能不是目标预览，请先确认。", images, bound);
        }
    }

    @Override
    public void cancelTask(String taskId) {
        requests.values().stream().filter(pending -> pending.getTaskId().equals(taskId))
                .forEach(pending -> finish(pending, BrowserScreenshotStatus.CANCELLED, "任务已结束或中断，截图请求取消。", List.of(), List.of()));
    }

    /**
     * 同步完成状态事件和工具结果，避免重复回传或超时覆盖已接收的图片。
     */
    private void finish(PendingBrowserScreenshot pending, BrowserScreenshotStatus status, String text,
                        List<ContentBlock> images, List<ConversationFileDo> files) {
        synchronized (pending) {
            if (pending.isCompleted()) {
                return;
            }
            pending.setCompleted(true);
            requests.remove(pending.getRequestId(), pending);
            pending.getEmitter().emit(new CustomEvent(AgentEventType.BROWSER_SCREENSHOT_COMPLETED.getValue(),
                    Map.of("requestId", pending.getRequestId(), "status", status.getValue(), "files", files)));
            List<ContentBlock> content = new ArrayList<>();
            content.add(TextBlock.builder().text(text).build());
            content.addAll(images);
            pending.getResult().tryEmitValue(ToolResultBlock.of(content));
        }
    }

    /**
     * 只预览 HTML 产物，文件 ID 必须来自本轮已发布的文件清单。
     */
    private boolean isHtml(ConversationFileDo file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".html") || name.endsWith(".htm");
    }
}
