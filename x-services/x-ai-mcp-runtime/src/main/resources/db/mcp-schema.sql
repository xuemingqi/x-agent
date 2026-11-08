CREATE TABLE IF NOT EXISTS agent_mcp_server
(
    id                             bigint                             not null comment 'MCP 服务配置 ID'
        primary key,
    name                           varchar(64)                        not null comment 'MCP 服务名称和工具命名空间',
    description                    varchar(500)                       not null comment 'MCP 服务用途描述',
    scope                          tinyint                            not null comment '作用域：1 公共，2 个人，3 公司内部公共',
    owner_user_id                  varchar(128) default ''            not null comment '个人 MCP 所属用户 ID，公共 MCP 为空字符串',
    transport_type                 tinyint                            not null comment '传输方式：1 Streamable HTTP，2 STDIO',
    credential_placement           tinyint                            null comment '用户凭证注入位置：1 HTTP Header，2 HTTP Query Parameter，3 STDIO Environment Variable',
    credential_key                 varchar(128)                       null comment '用户凭证注入名称',
    credential_group               varchar(64)                        null comment '公司内部公共 MCP 共享的用户凭证组',
    endpoint                       varchar(2048)                      null comment 'Streamable HTTP 服务地址',
    command                        varchar(512)                       null comment 'STDIO 启动命令',
    arguments_json                 longtext                          null comment 'STDIO 启动参数 JSON',
    headers_json                   longtext                          null comment 'Streamable HTTP Header 明文 JSON',
    query_parameters_json          longtext                          null comment 'Streamable HTTP 查询参数明文 JSON',
    environment_json               longtext                          null comment 'STDIO 环境变量明文 JSON',
    request_timeout_seconds        int      default 60                not null comment '请求超时时间，单位秒',
    initialization_timeout_seconds int      default 30                not null comment '初始化超时时间，单位秒',
    enabled                        tinyint  default 1                 not null comment '是否在 Agent 运行时启用',
    create_time                    datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time                    datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_agent_mcp_server_owner_name
        unique (scope, owner_user_id, name),
    index idx_agent_mcp_server_visible (scope, owner_user_id, enabled)
)
    comment '公共和个人 MCP 服务配置表' collate = utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS agent_mcp_user_credential
(
    id                  bigint                             not null comment '用户 MCP 凭证配置 ID'
        primary key,
    user_id             varchar(128)                       not null comment '用户 ID',
    credential_group    varchar(64)                        not null comment '公司内部公共 MCP 共享的凭证组',
    credential_value    varchar(2048)                      not null comment '用户 MCP 凭证值',
    create_time         datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time         datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_agent_mcp_user_credential
        unique (user_id, credential_group)
)
    comment '公司内部公共 MCP 用户凭证配置表' collate = utf8mb4_general_ci;
