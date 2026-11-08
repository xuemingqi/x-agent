package com.x.ai.common.model;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.state.AgentState;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 单次 Agent 订阅拥有的图片缓冲，主 Agent、子 Agent 和不同会话互不共享。
 */
@Getter
@Setter
public class ModelImageResponse {

    /**
     * 当前模型调用收到的图片块，下一次模型调用前清空。
     */
    private final List<ContentBlock> images = new ArrayList<>();

    /**
     * 最近一个模型分片，用于保留纯图片回复的消息标识。
     */
    private ChatResponse lastResponse;

    /**
     * 当前推理是否包含 SDK 可聚合的文字、思考或工具调用。
     */
    private boolean reasoningContent;

    /**
     * 纯图片响应的临时文字占位，仅供 SDK 建立消息，向外发送前移除。
     */
    private boolean textPlaceholder;

    /**
     * 当前调用所属的会话状态，避免从 Agent 的默认会话读取并发请求的状态。
     */
    private AgentState agentState;
}
