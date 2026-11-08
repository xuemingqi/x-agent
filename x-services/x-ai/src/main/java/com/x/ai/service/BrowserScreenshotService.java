package com.x.ai.service;

import com.x.ai.common.dto.BrowserScreenshotResultDto;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ToolResultBlock;
import reactor.core.publisher.Mono;

public interface BrowserScreenshotService {

    /**
     * 请求预览指定的本轮 HTML 产物并等待用户授权截图，仅支持 Web 多模态流式任务。
     */
    Mono<ToolResultBlock> request(String fileId, String reason, RuntimeContext context);

    /**
     * 校验当前登录用户、任务和附件归属，将截图或拒绝结果交还原工具调用。
     */
    void complete(String taskId, String requestId, BrowserScreenshotResultDto result);

    /**
     * 任务中断、结束或连接关闭时释放所有待处理截图请求。
     */
    void cancelTask(String taskId);
}
