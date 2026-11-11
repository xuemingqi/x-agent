package com.x.ai.db.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.x.ai.common.enums.McpTransportType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Builder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@TableName("agent_mcp_server")
public class AgentMcpServer {

    /**
     * MCP 服务配置主键。
     */
    @TableId("id")
    private Long id;

    /**
     * MCP 服务名称，同时用于生成 AgentScope 工具命名空间。
     */
    @TableField("name")
    private String name;

    /**
     * MCP 服务用途描述。
     */
    @TableField("description")
    private String description;

    /**
     * MCP 所属用户 ID。
     */
    @TableField("owner_user_id")
    private String ownerUserId;

    /**
     * MCP 传输方式。
     */
    @TableField("transport_type")
    private McpTransportType transportType;

    /**
     * Streamable HTTP 服务地址。
     */
    @TableField("endpoint")
    private String endpoint;

    /**
     * Streamable HTTP Header 明文 JSON。
     */
    @ToString.Exclude
    @TableField("headers_json")
    private String headersJson;

    /**
     * Streamable HTTP 查询参数明文 JSON。
     */
    @ToString.Exclude
    @TableField("query_parameters_json")
    private String queryParametersJson;

    /**
     * MCP 请求超时时间，单位秒。
     */
    @TableField("request_timeout_seconds")
    private Integer requestTimeoutSeconds;

    /**
     * MCP 初始化超时时间，单位秒。
     */
    @TableField("initialization_timeout_seconds")
    private Integer initializationTimeoutSeconds;

    /**
     * 是否在 Agent 运行时启用。
     */
    @TableField("enabled")
    private Boolean enabled;

    /**
     * 创建时间。
     */
    @TableField(value = "create_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    @TableField(value = "update_time", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updateTime;
}
