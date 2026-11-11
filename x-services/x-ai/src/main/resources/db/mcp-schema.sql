CREATE TABLE IF NOT EXISTS agent_mcp_server
(
    id                             bigint                             not null comment 'MCP 服务配置 ID'
        primary key,
    name                           varchar(64)                        not null comment 'MCP 服务名称和工具命名空间',
    description                    varchar(500)                       not null comment 'MCP 服务用途描述',
    owner_user_id                  varchar(128)                       not null comment 'MCP 所属用户 ID',
    transport_type                 tinyint                            not null comment '传输方式：1 Streamable HTTP',
    endpoint                       varchar(2048)                      null comment 'Streamable HTTP 服务地址',
    headers_json                   longtext                          null comment 'Streamable HTTP Header 明文 JSON',
    query_parameters_json          longtext                          null comment 'Streamable HTTP 查询参数明文 JSON',
    request_timeout_seconds        int      default 60                not null comment '请求超时时间，单位秒',
    initialization_timeout_seconds int      default 30                not null comment '初始化超时时间，单位秒',
    enabled                        tinyint  default 1                 not null comment '是否在 Agent 运行时启用',
    create_time                    datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time                    datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_agent_mcp_server_owner_name
        unique (owner_user_id, name),
    index idx_agent_mcp_server_visible (owner_user_id, enabled)
)
    comment '用户 MCP 服务配置表' collate = utf8mb4_general_ci;
