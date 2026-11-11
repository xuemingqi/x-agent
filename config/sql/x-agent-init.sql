-- X Agent: run once against a fresh MySQL 8 database. No user data included.
SET NAMES utf8mb4;

-- Source: x-services/x-contact/src/main/resources/db/user-schema.sql
-- Identity data needed by user ownership and login.
-- No sample accounts or passwords are included.
CREATE TABLE IF NOT EXISTS `user`
(
    id          bigint                              NOT NULL PRIMARY KEY,
    name        varchar(100)                        NOT NULL,
    sex         tinyint                             DEFAULT NULL,
    mobile      varchar(32)                         NOT NULL,
    password    varchar(255)                        NOT NULL,
    salt        varchar(128)                        NOT NULL,
    create_time datetime                            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time datetime                            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_mobile (mobile)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;


-- Source: x-services/x-ai/src/main/resources/db/agent-schema.sql
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


-- Source: x-services/x-ai/src/main/resources/db/mcp-schema.sql
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


-- Source: x-services/x-ai/src/main/resources/db/quartz-schema.sql
-- Quartz 2.5.2 official MySQL InnoDB schema.
-- Adaptation: QRTZ_ identifiers are renamed to AGENT_QRTZ_ to match application.yml.
-- Original MySQL InnoDB schema by Ron Cordell (roncordell), Quartz project.
-- Apache-2.0; see licenses/Apache-2.0.txt and THIRD_PARTY_NOTICES.md.
-- Adaptation also omits DROP TABLE statements. Run only against a fresh database.
CREATE TABLE AGENT_QRTZ_JOB_DETAILS
(
    SCHED_NAME        VARCHAR(120) NOT NULL,
    JOB_NAME          VARCHAR(190) NOT NULL,
    JOB_GROUP         VARCHAR(190) NOT NULL,
    DESCRIPTION       VARCHAR(250) NULL,
    JOB_CLASS_NAME    VARCHAR(250) NOT NULL,
    IS_DURABLE        VARCHAR(1)   NOT NULL,
    IS_NONCONCURRENT  VARCHAR(1)   NOT NULL,
    IS_UPDATE_DATA    VARCHAR(1)   NOT NULL,
    REQUESTS_RECOVERY VARCHAR(1)   NOT NULL,
    JOB_DATA          BLOB         NULL,
    PRIMARY KEY (SCHED_NAME, JOB_NAME, JOB_GROUP)
)
    ENGINE = InnoDB;

CREATE TABLE AGENT_QRTZ_TRIGGERS
(
    SCHED_NAME     VARCHAR(120) NOT NULL,
    TRIGGER_NAME   VARCHAR(190) NOT NULL,
    TRIGGER_GROUP  VARCHAR(190) NOT NULL,
    JOB_NAME       VARCHAR(190) NOT NULL,
    JOB_GROUP      VARCHAR(190) NOT NULL,
    DESCRIPTION    VARCHAR(250) NULL,
    NEXT_FIRE_TIME BIGINT(13)   NULL,
    PREV_FIRE_TIME BIGINT(13)   NULL,
    PRIORITY       INTEGER      NULL,
    TRIGGER_STATE  VARCHAR(16)  NOT NULL,
    TRIGGER_TYPE   VARCHAR(8)   NOT NULL,
    START_TIME     BIGINT(13)   NOT NULL,
    END_TIME       BIGINT(13)   NULL,
    CALENDAR_NAME  VARCHAR(190) NULL,
    MISFIRE_INSTR  SMALLINT(2)  NULL,
    JOB_DATA       BLOB         NULL,
    PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
    FOREIGN KEY (SCHED_NAME, JOB_NAME, JOB_GROUP)
        REFERENCES AGENT_QRTZ_JOB_DETAILS (SCHED_NAME, JOB_NAME, JOB_GROUP)
)
    ENGINE = InnoDB;

CREATE TABLE AGENT_QRTZ_SIMPLE_TRIGGERS
(
    SCHED_NAME      VARCHAR(120) NOT NULL,
    TRIGGER_NAME    VARCHAR(190) NOT NULL,
    TRIGGER_GROUP   VARCHAR(190) NOT NULL,
    REPEAT_COUNT    BIGINT(7)    NOT NULL,
    REPEAT_INTERVAL BIGINT(12)   NOT NULL,
    TIMES_TRIGGERED BIGINT(10)   NOT NULL,
    PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
    FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
        REFERENCES AGENT_QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
)
    ENGINE = InnoDB;

CREATE TABLE AGENT_QRTZ_CRON_TRIGGERS
(
    SCHED_NAME      VARCHAR(120) NOT NULL,
    TRIGGER_NAME    VARCHAR(190) NOT NULL,
    TRIGGER_GROUP   VARCHAR(190) NOT NULL,
    CRON_EXPRESSION VARCHAR(120) NOT NULL,
    TIME_ZONE_ID    VARCHAR(80),
    PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
    FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
        REFERENCES AGENT_QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
)
    ENGINE = InnoDB;

CREATE TABLE AGENT_QRTZ_SIMPROP_TRIGGERS
(
    SCHED_NAME    VARCHAR(120)   NOT NULL,
    TRIGGER_NAME  VARCHAR(190)   NOT NULL,
    TRIGGER_GROUP VARCHAR(190)   NOT NULL,
    STR_PROP_1    VARCHAR(512)   NULL,
    STR_PROP_2    VARCHAR(512)   NULL,
    STR_PROP_3    VARCHAR(512)   NULL,
    INT_PROP_1    INT            NULL,
    INT_PROP_2    INT            NULL,
    LONG_PROP_1   BIGINT         NULL,
    LONG_PROP_2   BIGINT         NULL,
    DEC_PROP_1    NUMERIC(13, 4) NULL,
    DEC_PROP_2    NUMERIC(13, 4) NULL,
    BOOL_PROP_1   VARCHAR(1)     NULL,
    BOOL_PROP_2   VARCHAR(1)     NULL,
    PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
    FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
        REFERENCES AGENT_QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
)
    ENGINE = InnoDB;

CREATE TABLE AGENT_QRTZ_BLOB_TRIGGERS
(
    SCHED_NAME    VARCHAR(120) NOT NULL,
    TRIGGER_NAME  VARCHAR(190) NOT NULL,
    TRIGGER_GROUP VARCHAR(190) NOT NULL,
    BLOB_DATA     BLOB         NULL,
    PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
    INDEX (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
    FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
        REFERENCES AGENT_QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
)
    ENGINE = InnoDB;

CREATE TABLE AGENT_QRTZ_CALENDARS
(
    SCHED_NAME    VARCHAR(120) NOT NULL,
    CALENDAR_NAME VARCHAR(190) NOT NULL,
    CALENDAR      BLOB         NOT NULL,
    PRIMARY KEY (SCHED_NAME, CALENDAR_NAME)
)
    ENGINE = InnoDB;

CREATE TABLE AGENT_QRTZ_PAUSED_TRIGGER_GRPS
(
    SCHED_NAME    VARCHAR(120) NOT NULL,
    TRIGGER_GROUP VARCHAR(190) NOT NULL,
    PRIMARY KEY (SCHED_NAME, TRIGGER_GROUP)
)
    ENGINE = InnoDB;

CREATE TABLE AGENT_QRTZ_FIRED_TRIGGERS
(
    SCHED_NAME        VARCHAR(120) NOT NULL,
    ENTRY_ID          VARCHAR(95)  NOT NULL,
    TRIGGER_NAME      VARCHAR(190) NOT NULL,
    TRIGGER_GROUP     VARCHAR(190) NOT NULL,
    INSTANCE_NAME     VARCHAR(190) NOT NULL,
    FIRED_TIME        BIGINT(13)   NOT NULL,
    SCHED_TIME        BIGINT(13)   NOT NULL,
    PRIORITY          INTEGER      NOT NULL,
    STATE             VARCHAR(16)  NOT NULL,
    JOB_NAME          VARCHAR(190) NULL,
    JOB_GROUP         VARCHAR(190) NULL,
    IS_NONCONCURRENT  VARCHAR(1)   NULL,
    REQUESTS_RECOVERY VARCHAR(1)   NULL,
    PRIMARY KEY (SCHED_NAME, ENTRY_ID)
)
    ENGINE = InnoDB;

CREATE TABLE AGENT_QRTZ_SCHEDULER_STATE
(
    SCHED_NAME        VARCHAR(120) NOT NULL,
    INSTANCE_NAME     VARCHAR(190) NOT NULL,
    LAST_CHECKIN_TIME BIGINT(13)   NOT NULL,
    CHECKIN_INTERVAL  BIGINT(13)   NOT NULL,
    PRIMARY KEY (SCHED_NAME, INSTANCE_NAME)
)
    ENGINE = InnoDB;

CREATE TABLE AGENT_QRTZ_LOCKS
(
    SCHED_NAME VARCHAR(120) NOT NULL,
    LOCK_NAME  VARCHAR(40)  NOT NULL,
    PRIMARY KEY (SCHED_NAME, LOCK_NAME)
)
    ENGINE = InnoDB;

CREATE INDEX IDX_AGENT_QRTZ_J_REQ_RECOVERY ON AGENT_QRTZ_JOB_DETAILS (SCHED_NAME, REQUESTS_RECOVERY);
CREATE INDEX IDX_AGENT_QRTZ_J_GRP ON AGENT_QRTZ_JOB_DETAILS (SCHED_NAME, JOB_GROUP);

CREATE INDEX IDX_AGENT_QRTZ_T_J ON AGENT_QRTZ_TRIGGERS (SCHED_NAME, JOB_NAME, JOB_GROUP);
CREATE INDEX IDX_AGENT_QRTZ_T_JG ON AGENT_QRTZ_TRIGGERS (SCHED_NAME, JOB_GROUP);
CREATE INDEX IDX_AGENT_QRTZ_T_C ON AGENT_QRTZ_TRIGGERS (SCHED_NAME, CALENDAR_NAME);
CREATE INDEX IDX_AGENT_QRTZ_T_G ON AGENT_QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_GROUP);
CREATE INDEX IDX_AGENT_QRTZ_T_STATE ON AGENT_QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_STATE);
CREATE INDEX IDX_AGENT_QRTZ_T_N_STATE ON AGENT_QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP, TRIGGER_STATE);
CREATE INDEX IDX_AGENT_QRTZ_T_N_G_STATE ON AGENT_QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_GROUP, TRIGGER_STATE);
CREATE INDEX IDX_AGENT_QRTZ_T_NEXT_FIRE_TIME ON AGENT_QRTZ_TRIGGERS (SCHED_NAME, NEXT_FIRE_TIME);
CREATE INDEX IDX_AGENT_QRTZ_T_NFT_ST ON AGENT_QRTZ_TRIGGERS (SCHED_NAME, TRIGGER_STATE, NEXT_FIRE_TIME);
CREATE INDEX IDX_AGENT_QRTZ_T_NFT_MISFIRE ON AGENT_QRTZ_TRIGGERS (SCHED_NAME, MISFIRE_INSTR, NEXT_FIRE_TIME);
CREATE INDEX IDX_AGENT_QRTZ_T_NFT_ST_MISFIRE ON AGENT_QRTZ_TRIGGERS (SCHED_NAME, MISFIRE_INSTR, NEXT_FIRE_TIME, TRIGGER_STATE);
CREATE INDEX IDX_AGENT_QRTZ_T_NFT_ST_MISFIRE_GRP ON AGENT_QRTZ_TRIGGERS (SCHED_NAME, MISFIRE_INSTR, NEXT_FIRE_TIME,
                                                                         TRIGGER_GROUP, TRIGGER_STATE);

CREATE INDEX IDX_AGENT_QRTZ_FT_TRIG_INST_NAME ON AGENT_QRTZ_FIRED_TRIGGERS (SCHED_NAME, INSTANCE_NAME);
CREATE INDEX IDX_AGENT_QRTZ_FT_INST_JOB_REQ_RCVRY ON AGENT_QRTZ_FIRED_TRIGGERS (SCHED_NAME, INSTANCE_NAME, REQUESTS_RECOVERY);
CREATE INDEX IDX_AGENT_QRTZ_FT_J_G ON AGENT_QRTZ_FIRED_TRIGGERS (SCHED_NAME, JOB_NAME, JOB_GROUP);
CREATE INDEX IDX_AGENT_QRTZ_FT_JG ON AGENT_QRTZ_FIRED_TRIGGERS (SCHED_NAME, JOB_GROUP);
CREATE INDEX IDX_AGENT_QRTZ_FT_T_G ON AGENT_QRTZ_FIRED_TRIGGERS (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP);
CREATE INDEX IDX_AGENT_QRTZ_FT_TG ON AGENT_QRTZ_FIRED_TRIGGERS (SCHED_NAME, TRIGGER_GROUP);

commit;
