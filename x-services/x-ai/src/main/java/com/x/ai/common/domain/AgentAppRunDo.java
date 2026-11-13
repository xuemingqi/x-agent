package com.x.ai.common.domain;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AgentAppRunDo {

    /**
     * 本次任务的最终文本，不返回工具连接配置或平台凭证。
     */
    private String text;

    /**
     * 仅标识本次执行的随机会话 ID，不支持恢复或读取平台聊天。
     */
    private String sessionId;
}
