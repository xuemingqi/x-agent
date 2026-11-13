-- Managed schema for Agent platform data. AgentScope distributed state is stored in Redis.
-- The application never creates database objects at startup.
CREATE TABLE IF NOT EXISTS ai_model_config
(
    id                   bigint                             not null comment '模型配置 ID'
        primary key,
    user_id              bigint                             not null comment '模型所属用户 ID',
    name                 varchar(100) charset utf8mb4       not null comment '模型展示名称',
    model_url            varchar(2048) charset utf8mb4      not null comment '模型接口地址',
    model_name           varchar(255) charset utf8mb4       not null comment '提供方实际模型名称',
    model_key            varchar(2048) charset utf8mb4      not null comment '模型接口密钥',
    context_window_size  int                                not null comment '模型上下文窗口大小，须填写提供方实际支持的 token 数',
    multimodal_capability tinyint default 0                 not null comment '多模态能力：0 纯文本，1 图片输入，2 图片输出，3 图片输入及输出',
    temperature          decimal(3, 2)                      null comment '模型温度参数',
    is_default           tinyint(1) default 0               not null comment '是否为当前用户外部调用使用的默认模型',
    default_model_marker tinyint generated always as (case when is_default = 1 then 1 else null end) stored,
    create_time          datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time          datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint chk_ai_model_config_multimodal_capability
        check (multimodal_capability in (0, 1, 2, 3)),
    constraint chk_ai_model_config_is_default
        check (is_default in (0, 1)),
    constraint uk_ai_model_config_user_default
        unique (user_id, default_model_marker),
    index idx_ai_model_config_user_update (user_id, update_time)
)
    comment '用户 AI 模型配置表' collate = utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS agent_api_key
(
    user_id     varchar(128)                             not null comment 'API Key 所属用户 ID'
        primary key,
    key_hash    char(64) charset ascii collate ascii_bin not null comment 'API Key SHA-256 摘要',
    create_time datetime default CURRENT_TIMESTAMP       not null comment '创建时间',
    update_time datetime default CURRENT_TIMESTAMP       not null on update CURRENT_TIMESTAMP comment '更新时间'
)
    comment '用户 API Key 表' collate = utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS agent_schedule_task
(
    id               bigint                             not null comment '主键id'
        primary key,
    task_name        varchar(100)                       null comment '任务名称',
    user_id          varchar(50)                        null comment '任务所属用户 ID',
    model_id         bigint                             null comment '用户选择的模型配置 ID',
    prompt           text                               null comment '定时执行的任务提示词',
    schedule_mode    tinyint                            null comment '调度模式',
    cron_expression  varchar(50)                        null comment 'Cron 表达式',
    fixed_rate_ms    mediumtext                         null comment '固定频率间隔',
    fixed_delay_ms   mediumtext                         null comment '固定延迟间隔',
    initial_delay_ms mediumtext                         null comment '首次执行延迟',
    status           tinyint                            null comment '任务业务状态',
    last_run_time         datetime                           null comment '最近一次执行时间',
    last_execution_status tinyint                            null comment '最近一次执行状态',
    create_time      datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time      datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间'
)
    comment '定时任务表' collate = utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS agent_schedule_execution
(
    id                  bigint                             not null comment '主键id'
        primary key,
    schedule_task_id    bigint                             not null comment '关联的定时任务 ID',
    trigger_source      tinyint                            null comment '本次执行的触发来源',
    scheduled_fire_time datetime                           null comment '计划触发时间',
    task_id             varchar(50) charset utf8mb4        null comment 'Agent 执行任务 ID',
    user_id             varchar(50) charset utf8mb4        null comment '任务所属用户 ID',
    model_id            bigint                             null comment '实际使用的模型配置 ID',
    status              tinyint                            null comment '执行状态',
    attempt_count       int                                null comment '已尝试执行次数',
    start_time          datetime                           null comment '开始执行时间',
    finish_time         datetime                           null comment '完成执行时间',
    result_summary      text charset utf8mb4               null comment '执行结果摘要',
    error_summary       text charset utf8mb4               null comment '执行错误摘要',
    create_time         datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time         datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间'
)
    comment '定时任务执行记录表' collate = utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS agent_knowledge_link_folder
(
    id            bigint                             not null comment '在线知识库文件夹 ID'
        primary key,
    parent_id     bigint default 0                   not null comment '父文件夹 ID，0 表示根目录',
    name          varchar(256)                       not null comment '文件夹名称',
    owner_user_id varchar(128)                       not null comment '所属用户 ID',
    create_time   datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint chk_agent_knowledge_link_folder_parent
        check (id > 0 and parent_id >= 0 and id <> parent_id),
    constraint uk_agent_knowledge_link_folder_name
        unique (owner_user_id, parent_id, name)
)
    comment '用户在线知识库文件夹表' collate = utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS agent_knowledge_link
(
    id            bigint                             not null comment '在线知识库链接 ID'
        primary key,
    folder_id     bigint default 0                   not null comment '所属文件夹 ID，0 表示根目录',
    name          varchar(256)                       not null comment '文档名称',
    summary       varchar(1000)                      not null comment '文档简短介绍',
    url           varchar(2048)                      not null comment '在线文档链接',
    url_hash      char(64) charset ascii collate ascii_bin not null comment '规范化 URL 的 SHA-256 摘要',
    owner_user_id varchar(128)                       not null comment '所属用户 ID',
    enabled       tinyint      default 1             not null comment '是否启用',
    create_time   datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint chk_agent_knowledge_link_enabled
        check (enabled in (0, 1)),
    constraint uk_agent_knowledge_link_url
        unique (owner_user_id, url_hash),
    index idx_agent_knowledge_link_visible (owner_user_id, enabled),
    index idx_agent_knowledge_link_folder (owner_user_id, folder_id)
)
    comment '用户在线知识库链接表' collate = utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS agent_conversation
(
    session_id  varchar(64)                        not null comment '会话 ID'
        primary key,
    user_id     varchar(64)                        not null comment '所属用户 ID',
    title       varchar(255)                       not null comment '对话标题',
    knowledge_base_ids json                        null comment '额外关联的个人知识库 ID 数组，公共知识库自动关联',
    create_time datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    index idx_agent_conversation_user_time (user_id, update_time)
)
    comment '用户对话元数据表' collate = utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS agent_conversation_message
(
    id           bigint                              not null comment '消息 ID'
        primary key,
    parent_id    bigint                              null comment '父消息 ID',
    session_id   varchar(64)                         not null comment '会话 ID',
    user_id      varchar(64)                         not null comment '所属用户 ID',
    role         varchar(32)                         not null comment '消息角色',
    content      longtext                            not null comment '面向用户展示的消息内容',
    tool_call_id varchar(128)                        null comment '工具调用 ID',
    token_usage  json                                null comment '本轮模型累计用量',
    create_time  datetime(3) default CURRENT_TIMESTAMP(3) not null comment '创建时间',
    index idx_agent_conversation_message_session (user_id, session_id, id)
)
    comment '用户对话消息表' collate = utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS agent_conversation_file
(
    id           bigint                                   not null comment '文件 ID'
        primary key,
    message_id   bigint                                   null comment '文件所属的用户消息 ID，上传后未发送时为空',
    session_id   varchar(64)                              not null comment '会话 ID',
    user_id      varchar(64)                              not null comment '所属用户 ID',
    kind         tinyint                                  not null comment '文件类型：1 用户附件，2 Agent 产物',
    file_name    varchar(255)                             not null comment '展示和下载使用的文件名',
    object_key   varchar(1024)                            not null comment 'MinIO 对象 Key',
    sandbox_path varchar(1024)                            not null comment 'AgentScope 工作区相对路径',
    content_type varchar(255) default 'application/octet-stream' not null comment '文件媒体类型',
    size_bytes   bigint                                   not null comment '文件字节数',
    create_time  datetime(3) default CURRENT_TIMESTAMP(3) not null comment '创建时间'
)
    comment '用户会话文件表' collate = utf8mb4_general_ci;

-- User preferences only; existing resources remain enabled by default.
CREATE TABLE IF NOT EXISTS agent_disabled_resource
(
    id           char(64) charset ascii collate ascii_bin not null comment '用户与资源键的 SHA-256 摘要'
        primary key,
    user_id      varchar(128) collate utf8mb4_bin         not null comment '关闭扩展的用户 ID',
    resource_key varchar(256) collate utf8mb4_bin         not null comment 'SKILL:名称',
    create_time  datetime default CURRENT_TIMESTAMP      not null comment '关闭时间',
    constraint uk_agent_disabled_resource
        unique (user_id, resource_key)
)
    ENGINE = InnoDB comment '用户关闭的扩展资源，未记录的资源默认启用' collate = utf8mb4_bin;
-- 个人 Skill 的正文、元数据与附件统一持久化到数据库。
CREATE TABLE IF NOT EXISTS agent_user_skill (
    id BIGINT NOT NULL COMMENT '个人 Skill ID',
    user_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '所属用户 ID',
    name VARCHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT '用户内唯一名称',
    description VARCHAR(500) NOT NULL COMMENT '使用场景',
    skill_content LONGTEXT NOT NULL COMMENT '指令正文，不含 frontmatter',
    metadata JSON NOT NULL COMMENT '完整 frontmatter',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_skill_name (user_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个人 Skill';

CREATE TABLE IF NOT EXISTS agent_user_skill_resource (
    id BIGINT NOT NULL COMMENT '资源 ID',
    skill_id BIGINT NOT NULL COMMENT '所属个人 Skill ID',
    resource_path VARCHAR(1024) COLLATE utf8mb4_bin NOT NULL COMMENT 'Skill 内相对路径',
    resource_content LONGBLOB NOT NULL COMMENT '原始资源字节',
    PRIMARY KEY (id),
    KEY idx_user_skill_resource_skill (skill_id),
    CONSTRAINT fk_user_skill_resource FOREIGN KEY (skill_id) REFERENCES agent_user_skill (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个人 Skill 附件';


-- Application source packages are immutable private MinIO objects; MySQL stores references only.
CREATE TABLE IF NOT EXISTS agent_app
(
    id              varchar(64)                            not null comment '个人应用 ID' primary key,
    owner_user_id   varchar(128)                           not null comment '所属用户 ID',
    name            varchar(100)                           not null comment '当前发布版本的应用名称',
    description     varchar(1000)                          not null comment '当前发布版本的应用介绍',
    current_version varchar(64)                            not null comment '当前发布版本 ID',
    deleted         tinyint(1) default 0                    not null comment '是否进入删除流程，失败时保留对象引用供重试',
    create_time     datetime default CURRENT_TIMESTAMP     not null comment '创建时间',
    update_time     datetime default CURRENT_TIMESTAMP     not null on update CURRENT_TIMESTAMP comment '更新时间',
    index idx_agent_app_owner_update (owner_user_id, deleted, update_time),
    constraint chk_agent_app_deleted check (deleted in (0, 1))
)
    comment '用户动态智能应用目录' collate = utf8mb4_bin;

CREATE TABLE IF NOT EXISTS agent_app_version
(
    id          varchar(64)                            not null comment '不可变应用版本 ID' primary key,
    app_id      varchar(64)                            not null comment '所属应用 ID',
    name        varchar(100)                           not null comment '发布时的应用名称',
    description varchar(1000)                          not null comment '发布时的应用介绍',
    object_key  varchar(1024)                          not null comment 'MinIO 私有应用包对象键',
    size_bytes  bigint                                 not null comment '应用包 UTF-8 字节数',
    sha256      char(64) charset ascii collate ascii_bin not null comment '应用包 SHA-256 摘要',
    create_time datetime default CURRENT_TIMESTAMP     not null comment '版本创建时间',
    index idx_agent_app_version_app_time (app_id, create_time),
    constraint fk_agent_app_version_app foreign key (app_id) references agent_app (id) on delete cascade
)
    comment '智能应用不可变版本及对象存储引用' collate = utf8mb4_bin;

CREATE TABLE IF NOT EXISTS agent_app_data
(
    id            bigint                                  not null comment '应用数据记录 ID' primary key,
    owner_user_id varchar(128)                            not null comment '数据所属用户 ID',
    app_id        varchar(64)                             not null comment '数据所属应用 ID',
    data_key      varchar(128)                            not null comment '应用内部的数据键，区分大小写',
    value_json    mediumtext                              not null comment '应用保存的 JSON 值，最大 1 MiB',
    create_time   datetime default CURRENT_TIMESTAMP      not null comment '创建时间',
    update_time   datetime default CURRENT_TIMESTAMP      not null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_agent_app_data_owner_app_key unique (owner_user_id, app_id, data_key),
    index idx_agent_app_data_app (app_id),
    constraint fk_agent_app_data_app foreign key (app_id) references agent_app (id) on delete cascade
)
    comment '用户应用隔离的数据存储' collate = utf8mb4_bin;
