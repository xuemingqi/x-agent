package com.x.ai.common.model;

import io.agentscope.core.event.AgentEventEmitter;
import io.agentscope.core.message.ToolResultBlock;
import lombok.Data;
import lombok.experimental.Accessors;
import reactor.core.publisher.Sinks;

@Data
@Accessors(chain = true)
public class PendingBrowserScreenshot {

    /**
     * 一次性截图请求 ID。
     */
    private String requestId;

    /**
     * 发起请求的任务 ID。
     */
    private String taskId;

    /**
     * 请求所属用户，不接收客户端指定的用户身份。
     */
    private String userId;

    /**
     * 截图附件必须归属的会话 ID。
     */
    private String sessionId;

    /**
     * 截图附件所属的本轮用户消息 ID。
     */
    private Long messageId;

    /**
     * 截图请求失效的 Unix 毫秒时间。
     */
    private long expiresAt;

    /**
     * 当前执行的事件通道，只用于发送截图状态，不发送图片字节。
     */
    private AgentEventEmitter emitter;

    /**
     * 当前工具等待的原生多模态结果。
     */
    private final Sinks.One<ToolResultBlock> result = Sinks.one();

    /**
     * 已完成或失效；在请求对象锁内读写，防止重复提交与超时竞争。
     */
    private boolean completed;
}
