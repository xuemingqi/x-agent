package com.x.ai.common.model;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 已完成用户、模型及附件授权的一次会话执行。
 */
@Data
@Accessors(chain = true)
public class PreparedAgentExecution {

    /**
     * 本次执行任务 ID，用于中断和截图归属校验。
     */
    private String taskId;

    /**
     * 本轮用户消息 ID；无持久会话的调用可为空。
     */
    private Long messageId;

    /**
     * 当前用户与模型对应的独享 Agent。
     */
    private HarnessAgent agent;

    /**
     * 本次调用的可信身份及会话上下文。
     */
    private RuntimeContext context;

    /**
     * 已绑定附件与原生图片的用户输入。
     */
    private UserMessage input;

    /**
     * 当前模型是否允许图片输入与授权截图。
     */
    private boolean imageInput;
}
