package com.x.ai.common.constants;

import cn.hutool.core.text.StrPool;

/**
 * @author : xuemingqi
 * @date : 2026/7/13 15:05
 */
public interface AgentConstant {

    String AGENT_SCOPE_REDIS_KEY_PREFIX = "xagent:agentscope:";

    /**
     * 定时任务相关
     */
    String SCHEDULE_TASK_ID = "scheduleTaskId";

    String SCHEDULE_EXECUTION_ID = "scheduleExecutionId";

    String SCHEDULE_SESSION_PREFIX = "schedule-execution-";

    String SCHEDULE_JOB_PREFIX = "agent-task-";

    String SCHEDULE_TRIGGER_PREFIX = "agent-trigger-";

    long SCHEDULE_MILLIS_PER_SECOND = 1000L;

    /**
     * 会话文件发布工具名称。
     */
    String CONVERSATION_FILE_PUBLISH_TOOL_NAME = "conversation_file_publish";

    /**
     * 请求 Web 用户授权截图并返回图片的工具名。
     */
    String BROWSER_SCREENSHOT_TOOL_NAME = "request_browser_screenshot";

    /**
     * 个人 Skill 数据仓库的来源标识。
     */
    String USER_SKILL_SOURCE = "user-workspace";

    /**
     * 会话文件在沙箱工作区中的根目录。
     */
    String CONVERSATION_FILE_WORKSPACE_DIRECTORY = "conversation-files";

    /**
     * 会话输入文件目录。
     */
    String CONVERSATION_FILE_INPUT_DIRECTORY = "inputs";

    /**
     * 会话输出文件目录。
     */
    String CONVERSATION_FILE_OUTPUT_DIRECTORY = "outputs";

    /**
     * 当前会话输入文件目录，用于向 Agent 描述工作区约定。
     */
    String CONVERSATION_FILE_CURRENT_SESSION_INPUT_PATH = CONVERSATION_FILE_WORKSPACE_DIRECTORY
            + StrPool.SLASH + "{当前会话ID}" + StrPool.SLASH
            + CONVERSATION_FILE_INPUT_DIRECTORY + StrPool.SLASH;

    /**
     * 当前会话输出文件目录，用于向 Agent 描述工作区约定。
     */
    String CONVERSATION_FILE_CURRENT_SESSION_OUTPUT_PATH = CONVERSATION_FILE_WORKSPACE_DIRECTORY
            + StrPool.SLASH + "{当前会话ID}" + StrPool.SLASH
            + CONVERSATION_FILE_OUTPUT_DIRECTORY + StrPool.SLASH;

    /**
     * 带会话 ID 占位符的输出目录模板。
     */
    String CONVERSATION_FILE_OUTPUT_PATH_TEMPLATE = CONVERSATION_FILE_WORKSPACE_DIRECTORY
            + StrPool.SLASH + "%s" + StrPool.SLASH
            + CONVERSATION_FILE_OUTPUT_DIRECTORY + StrPool.SLASH;

    /**
     * 共享 Skill 的发送人手机号元数据键。
     */
    String SHARED_SKILL_SENDER_MOBILE_METADATA = "sharedByMobile";
}
