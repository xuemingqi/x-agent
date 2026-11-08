package com.x.ai.common.model;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class BrowserScreenshotContext {

    /**
     * 所属 Web 流式任务 ID，用于隔离回传与中断清理。
     */
    private String taskId;

    /**
     * 本轮用户消息 ID，截图附件归入本轮会话记录。
     */
    private Long messageId;
}
