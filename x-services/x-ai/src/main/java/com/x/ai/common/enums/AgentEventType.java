package com.x.ai.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Agent 流式响应事件类型。
 */
@Getter
@AllArgsConstructor
public enum AgentEventType {

    TASK_STARTED("task_started", "任务开始"),

    HEARTBEAT("heartbeat", "任务连接保活"),

    BROWSER_SCREENSHOT_REQUESTED("browser_screenshot_requested", "等待用户授权截图"),

    BROWSER_SCREENSHOT_COMPLETED("browser_screenshot_completed", "截图请求结束"),

    AGENT_STARTED("agent_started", "Agent 开始"),

    TEXT_DELTA("text_delta", "文本增量"),

    THINKING_BLOCK_DELTA("thinking_block_delta", "思考增量"),

    TOOL_CALL_START("tool_call_start", "工具调用开始"),

    TOOL_CALL_END("tool_call_end", "工具调用结束"),

    TOOL_RESULT_END("tool_result_end", "工具结果结束"),

    TOOL_RESULT_TEXT_DELTA("tool_result_text_delta", "工具结果文本增量"),

    AGENT_RESULT("agent_result", "Agent 最终结果"),

    AGENT_END("agent_end", "Agent 结束"),

    TASK_END("task_end", "任务结束"),

    UNKNOWN("unknown", "未识别事件");

    @JsonValue
    private final String value;

    private final String desc;
}
