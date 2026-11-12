package com.x.framework.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Optional;

/**
 * 通用的返回码
 */
@Getter
@AllArgsConstructor
public enum ResponseCodeEnum {

    /**
     * 基础
     */
    SUCCESS(HttpStatus.OK, 200, "success!"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, 400, "参数错误！"),
    FILE_NOT_FOUND(HttpStatus.BAD_REQUEST, 404, "不存在"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, 405, "请求方式错误！"),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, 429, "too many requests"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, 500, "发生未知错误，请稍后重试！"),

    /**
     * auth模块
     */
    PERMISSION_ERROR(HttpStatus.BAD_REQUEST, 10400, "token过期！"),
    VERIFICATION_ERROR(HttpStatus.BAD_REQUEST, 10401, "验证码错误！"),
    CONTACT_TOKEN_ERROR(HttpStatus.BAD_REQUEST, 10402, "账号或密码错误，您还可以尝试{}次。连续累计输错五次，账号将锁定30分钟。或通过忘记密码进行重置。如您未注册，请先申请账号。"),
    MOBILE_LOCK_ERROR(HttpStatus.BAD_REQUEST, 10403, "账号锁定30分钟，请稍后重试。"),
    INTERNAL_ERROR(HttpStatus.BAD_REQUEST, 10400, "内部token过期！"),

    /**
     * contact模块
     */
    USER_NOT_EXIST(HttpStatus.BAD_REQUEST, 11001, "用户不存在"),
    MOBILE_EXIST(HttpStatus.BAD_REQUEST, 11002, "手机号已经存在"),
    CREATE_USER_ERROR(HttpStatus.BAD_REQUEST, 11003, "添加用户失败"),
    USER_PWD_ERROR(HttpStatus.BAD_REQUEST, 11004, "密码错误"),

    /**
     * gateway模块
     */
    FLOW_BLOCK(HttpStatus.TOO_MANY_REQUESTS, 13001, "flow block"),
    DEGRADE_BLOCK(HttpStatus.BAD_REQUEST, 13002, "degrade block"),
    PARAM_FLOW_BLOCK(HttpStatus.BAD_REQUEST, 13003, "param flow block"),
    SYSTEM_BLOCK(HttpStatus.BAD_REQUEST, 13004, "system block"),
    AUTHORITY_BLOCK(HttpStatus.BAD_REQUEST, 13005, "authority block"),
    SENTINEL_BLOCK(HttpStatus.BAD_REQUEST, 13006, "sentinel block"),

    /**
     * ai模块
     */
    AGENT_APPROVAL_EXPIRED(HttpStatus.BAD_REQUEST, 15001, "审批请求已过期，请重新发起任务"),
    AGENT_APPROVAL_TOOL_CALL_NOT_FOUND(HttpStatus.BAD_REQUEST, 15002, "未找到待审批的工具调用"),
    AGENT_CONFIG_INVALID(HttpStatus.BAD_REQUEST, 15010, "Agent 配置错误！"),
    AGENT_MODEL_UNAVAILABLE(HttpStatus.BAD_REQUEST, 15011, "模型配置不可用！"),
    AGENT_RUNTIME_INVALID(HttpStatus.BAD_REQUEST, 15012, "Agent 运行状态错误！"),
    AGENT_WORKSPACE_STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, 15013, "用户工作区操作失败！"),
    AGENT_SCHEDULE_INVALID(HttpStatus.BAD_REQUEST, 15020, "定时任务配置错误！"),
    AGENT_SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, 15021, "定时任务不存在！"),
    AGENT_SCHEDULE_CONFLICT(HttpStatus.CONFLICT, 15022, "定时任务状态冲突！"),
    QUARTZ_SCHEDULER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, 15023, "定时任务同步失败！"),
    AGENT_EXECUTION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, 15030, "Agent 执行失败！"),
    AGENT_KNOWLEDGE_INVALID(HttpStatus.BAD_REQUEST, 15031, "知识库文件无效！"),
    AGENT_KNOWLEDGE_NOT_FOUND(HttpStatus.NOT_FOUND, 15032, "知识库文件不存在！"),
    AGENT_KNOWLEDGE_STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, 15033, "知识库文件操作失败！"),
    AGENT_KNOWLEDGE_INDEX_ERROR(HttpStatus.SERVICE_UNAVAILABLE, 15034, "知识库索引失败，请检查文档内容、向量模型及数据库配置！"),
    AGENT_KNOWLEDGE_SEARCH_INVALID(HttpStatus.BAD_REQUEST, 15035, "请输入有效检索内容，结果数应为 1 到 20！"),
    AGENT_KNOWLEDGE_BASE_INVALID(HttpStatus.BAD_REQUEST, 15036, "知识库名称或说明无效！"),
    AGENT_KNOWLEDGE_BASE_NOT_FOUND(HttpStatus.NOT_FOUND, 15037, "知识库不存在！"),
    AGENT_KNOWLEDGE_BASE_DEFAULT_REQUIRED(HttpStatus.BAD_REQUEST, 15038, "公共知识库用于所有对话，不能删除！"),
    AGENT_SKILL_INVALID(HttpStatus.BAD_REQUEST, 15040, "Skill 配置无效！"),
    AGENT_SKILL_NOT_FOUND(HttpStatus.NOT_FOUND, 15041, "Skill 不存在！"),
    AGENT_SKILL_CONFLICT(HttpStatus.CONFLICT, 15042, "Skill 已存在！"),
    AGENT_CONVERSATION_FILE_INVALID(HttpStatus.BAD_REQUEST, 15050, "会话文件无效！"),
    AGENT_CONVERSATION_FILE_NOT_FOUND(HttpStatus.NOT_FOUND, 15051, "会话文件不存在！"),
    AGENT_CONVERSATION_FILE_STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, 15052, "会话文件存储失败！"),
    AGENT_MCP_INVALID(HttpStatus.BAD_REQUEST, 15060, "MCP 配置无效！"),
    AGENT_MCP_NOT_FOUND(HttpStatus.NOT_FOUND, 15061, "MCP 配置不存在！"),
    AGENT_MCP_CONFLICT(HttpStatus.CONFLICT, 15062, "MCP 名称已存在！"),
    AGENT_MCP_TRANSPORT_UNSUPPORTED(HttpStatus.BAD_REQUEST, 15063, "当前版本不支持运行该 MCP 传输方式！"),
    AGENT_MCP_CONFIG_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, 15064, "MCP 持久化配置不可用！"),
    AGENT_MCP_CONNECTION_ERROR(HttpStatus.BAD_GATEWAY, 15065, "MCP 服务连接失败！"),
    API_KEY_INVALID(HttpStatus.UNAUTHORIZED, 15070, "API Key 无效或已失效！"),
    AGENT_KNOWLEDGE_LINK_INVALID(HttpStatus.BAD_REQUEST, 15100, "在线知识库链接无效！"),
    AGENT_KNOWLEDGE_LINK_NOT_FOUND(HttpStatus.NOT_FOUND, 15101, "在线知识库链接不存在！"),
    AGENT_KNOWLEDGE_LINK_CONFLICT(HttpStatus.CONFLICT, 15102, "在线知识库链接已存在！"),
    AGENT_KNOWLEDGE_FOLDER_INVALID(HttpStatus.BAD_REQUEST, 15103, "在线知识库文件夹无效！"),
    AGENT_KNOWLEDGE_FOLDER_NOT_FOUND(HttpStatus.NOT_FOUND, 15104, "在线知识库文件夹不存在！"),
    AGENT_KNOWLEDGE_FOLDER_CONFLICT(HttpStatus.CONFLICT, 15105, "当前目录下已存在同名文件夹！"),
    AGENT_KNOWLEDGE_FOLDER_NOT_EMPTY(HttpStatus.CONFLICT, 15106, "文件夹中仍有文档或子文件夹，请先移出或删除！"),
    AGENT_SKILL_ARCHIVE_INVALID(HttpStatus.BAD_REQUEST, 15120, "Skill 压缩包无效：请使用 ZIP、TAR 或 TAR.GZ，包含一个 SKILL.md，且文件不超过 20MB、解包不超过 50MB/500 项，不含链接或越界路径！"),
    AGENT_SKILL_IMPORT_MODEL_ERROR(HttpStatus.BAD_GATEWAY, 15121, "默认模型未能完成 Skill 整理，请检查模型配置后重试！"),
    AGENT_CONVERSATION_IMAGE_INVALID(HttpStatus.BAD_REQUEST, 15122, "图片无效或超过大小限制，请使用 PNG、JPEG、GIF 或 WebP 图片！"),
    AGENT_MODEL_IMAGE_HOST_DENIED(HttpStatus.BAD_GATEWAY, 15123, "模型图片地址未配置为可信域名，请配置 agent.multimodal.image-hosts！"),
    AGENT_MODEL_IMAGE_DOWNLOAD_ERROR(HttpStatus.BAD_GATEWAY, 15124, "模型图片下载失败！"),
    AGENT_APP_GENERATION_ERROR(HttpStatus.BAD_GATEWAY, 15125, "应用生成失败，请检查模型是否支持结构化输出，或稍后重试！"),
    AGENT_APP_OUTPUT_INVALID(HttpStatus.BAD_GATEWAY, 15126, "模型返回的应用结果不完整或格式无效，请重试！"),
    AGENT_APP_REGISTRATION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, 15127, "智能应用注册失败，应用标识无效或重复！"),
    HTTP_LAB_REQUEST_INVALID(HttpStatus.BAD_REQUEST, 15128, "HTTP 请求配置无效，请检查地址、请求头和参数！"),
    HTTP_LAB_NOT_FOUND(HttpStatus.NOT_FOUND, 15129, "请求或运行记录不存在、已过期，或不属于当前用户！"),
    HTTP_LAB_BUSY(HttpStatus.TOO_MANY_REQUESTS, 15130, "已有运行中的任务或执行资源已满，请稍后重试！");

    private final HttpStatus httpStatus;
    private final Integer code;
    private final String msg;

    /**
     * 按业务响应码查找预设错误，未知响应码由调用边界处理。
     */
    public static Optional<ResponseCodeEnum> fromCode(int code) {
        for (ResponseCodeEnum responseCode : values()) {
            if (responseCode.code == code) {
                return Optional.of(responseCode);
            }
        }
        return Optional.empty();
    }
}
