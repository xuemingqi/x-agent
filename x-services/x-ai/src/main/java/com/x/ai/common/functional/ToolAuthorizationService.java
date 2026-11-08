package com.x.ai.common.functional;

import com.x.ai.common.model.ToolContext;
import io.agentscope.core.message.ToolUseBlock;

@FunctionalInterface
public interface ToolAuthorizationService {

    /**
     * 校验当前用户是否获准执行指定工具调用。
     *
     * @param toolContext 可信工具上下文
     * @param toolCall    待授权的工具调用
     */
    void authorize(ToolContext toolContext, ToolUseBlock toolCall);
}
