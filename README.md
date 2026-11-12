# X Agent

基于 Java 21、Spring Boot 4、Spring Cloud Alibaba 和 AgentScope Java 的独立 AI Agent 平台，采用 [MIT 协议](LICENSE)。

支持多用户登录、个人模型配置、流式对话、会话与文件、Docker 沙箱、个人 Skill、知识库、MCP 管理、定时任务、浏览器截图、多模态和智能应用，并通过 HTTP Open API 与 MCP 对外提供 Agent 能力。

## 项目结构

```text
x-parent                 父依赖、版本和构建管理
x-apis                   服务间 Feign 契约
  x-auth-api             Token 校验
  x-contact-api          用户身份与账号
  x-ai-api               AI / Open API
x-framework              common / core / web / db / redis / minio
x-services
  x-auth                 登录、验证码、JWT、退出及失败锁定
  x-contact              用户认证、身份查询、账号创建
  x-gateway              路由、追踪、文档及 Sentinel 规则
  x-ai                   AgentScope、模型、会话、知识、Skill、任务与 MCP
x-webs/x-ai-web           Vue 3 + TypeScript 工作台
config/nacos             可直接导入的公开 Nacos 配置
config/x-agent-nacos.zip  Nacos 控制台原生 ZIP 导入包
config/sql               空数据库初始化 SQL
scripts                  配置导入、SQL 与初始账号工具
```

根 `pom.xml` 仅增加统一构建入口，继续保留 `x-parent / x-apis / x-framework / x-services` 分层。项目坐标版本为 `0.1.0-SNAPSHOT`，避免与旧项目产物混用。

## 环境

- JDK 21、Maven 3.9+
- Node.js 22.22.2+（22.x LTS）、npm（仅前端构建需要）
- Nacos 2.x（本项目验证版本为 2.4.2）
- MySQL 8、Redis、MinIO、PostgreSQL 13+ 与 pgvector
- Ollama 及可用的 embedding 模型，模型名称与维度通过 Nacos 配置
- Docker CLI 和可连接的 Docker daemon（Agent 沙箱）
- Python 3（配置和初始化辅助脚本，无第三方 Python 依赖）

## 1. 准备配置

复制 `.env.example` 为 `.env`，填写自己的 Nacos、MySQL、Redis、MinIO 参数。生成两个不同的随机值，分别填入 `X_AGENT_INTERNAL_SECRET` 和 `X_AGENT_JWT_SECRET`：

```bash
openssl rand -hex 32
```

两种签名密钥均至少 32 字符，内部签名密钥在各服务必须一致。`.env` 使用 `KEY=value` 格式，填写实际值；含空格或特殊字符的值加引号。不要把真实值写回模板或提交 `.env`。

本地通过 IDEA 加载根目录这同一份 `.env`，每个服务启动时都会获得所需变量，配置方式见第 4 节。

手动运行 Java 或执行 Nacos 导入脚本时，在命令所在终端加载这些变量：

```bash
set -a
source .env
set +a
```

Nacos 中的 `${MYSQL_PASSWORD}` 等占位符由 **各服务 JVM 的环境变量**解析。Nacos 自己不会自动读取本机 `.env`。在 IDE 中运行时，同样需要向各运行配置传入环境变量。

## 2. 导入 Nacos

创建独立命名空间，默认 ID 和名称均为 `x-agent-dev`。服务账号必须有该命名空间的配置读取和服务注册权限。配置中心与服务发现使用同一个 namespace/group。

任选一种方式：

- 控制台选择该命名空间，点击“导入配置”，上传 `config/x-agent-nacos.zip`。
- 已加载 `.env` 后运行脚本：

```bash
python3 scripts/nacos.py import --create-namespace
python3 scripts/nacos.py verify
```

共 10 项：`DEFAULT_GROUP` 下 1 份公共配置、4 份服务配置，`SENTINEL_GROUP` 下 5 份规则。规则初始为空，可按部署需要配置。重复导入默认跳过相同内容；发现不同内容会先停止。确需更新时检查差异后使用 `--overwrite`。

修改 YAML 后重新生成 ZIP：

```bash
python3 scripts/nacos.py package
```

`NACOS_GROUP` 自定义时须重新打包；Sentinel 规则仍使用 `SENTINEL_GROUP`。仓库中的应用配置只承担 Nacos 引导，不包含个人在线地址或凭据；远程配置缺失会在启动时报告错误。

## 3. 初始化数据库和账号

为项目创建独立数据库（示例名称 `x_agent`），对**空库**执行：

```bash
mysql -h 127.0.0.1 -u root -p -e 'CREATE DATABASE x_agent CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;'
mysql -h 127.0.0.1 -u root -p x_agent < config/sql/x-agent-init.sql
```

SQL 包含用户、AI、MCP、Skill 和 Quartz 表，无私人模型、Key、会话或默认账号。不要把初始化包重复应用到已有数据库；增量升级应单独审核。数据库名称应与 `.env` 中的 `MYSQL_DATABASE` 一致。

生成首个普通用户的 INSERT（交互输入密码，不会输出明文密码）：

```bash
umask 077
python3 scripts/create-user-sql.py --mobile YOUR_LOGIN_ID --name User > /tmp/x-agent-user.sql
mysql -h 127.0.0.1 -u root -p x_agent < /tmp/x-agent-user.sql
rm /tmp/x-agent-user.sql
```

`--mobile` 是登录标识，与登录页面填写的值一致。默认初始 ID 为 1；重复执行不会覆盖已有账号。后续可通过 `/contact/user/create` 创建用户。平台只有普通用户一种身份，每人仅维护自己的模型、Skill、知识库和 MCP。

改动实体结构后，可运行 `python3 scripts/build-init-sql.py` 重新汇总初始化包。

### MCP 合并与已有数据库升级

MCP 配置、远程连接及对外 Agent 工具统一由 `x-ai` 提供，仅支持 Streamable HTTP。
AI 实例直接连接远程 MCP 服务；对外 `/mcp` 使用无状态 HTTP 传输，可由 Gateway 分发到多个 AI 实例。
无需部署独立 MCP Runtime 或 MCP Gateway，也无需管理本地子进程和运行时代理。

升级已有环境时，先停止两个旧 MCP 服务和旧版 AI 进程，备份数据库，再执行 `config/sql/20261111-mcp-http-only.sql`。
该脚本保留 HTTP MCP 配置，删除旧 STDIO 配置、仅供这些配置使用的凭据及命令、参数和环境变量列；
旧 STDIO 配置不迁移，需改为可远程访问的 Streamable HTTP 服务后重新添加。
移除两个旧 MCP 服务，更新 Nacos 中的 `x-gateway.yaml`，删除旧服务的 Nacos 配置。
再按下面的用户私有资源升级步骤完成数据库迁移，最后统一部署新版服务与前端。

### 用户私有资源升级

本版本移除管理员、公共 Skill、公共 MCP、公共文档和公共在线知识库；接口不再接受资源作用域。
MCP 的 Header/Query 凭据直接保存在用户自己的配置中，启用状态也由该配置统一管理。
Skill 只提供个人创建、编辑、导入、导出和启用管理，不再提供分享、接收邀请或跨用户快照。
旧版已接收的 Skill 在升级时保留为个人内容，同名个人 Skill 不会被覆盖。
Agent 缓存按用户的模型、Skill 和 MCP 摘要刷新，无需查询管理员身份或维护全局公共资源版本。

已有环境先备份 MySQL 与 PostgreSQL，停止所有旧 AI 和 Contact 实例，然后依次执行：

1. 尚未完成 MCP HTTP 改造的环境，执行 `config/sql/20261111-mcp-http-only.sql`。
2. 在 MySQL 执行 `config/sql/20261111-user-owned-resources.sql`，删除公共资源与凭据及管理员字段，
   将已接受的分享快照迁移为个人副本，并迁移 Skill/MCP 启用偏好。
3. 在 MySQL 执行 `config/sql/20261111-remove-skill-sharing.sql`，删除旧分享关系、快照和快照附件表。
4. 在 PostgreSQL 知识库执行 `config/sql/20261111-user-owned-knowledge-postgresql.sql`，清理公共文档与切片。
5. 更新 Nacos 配置（移除 `agent.skill` 公共仓库配置），同时部署新版 Contact、AI 和前端。

已经完成用户私有资源升级的环境，只需停止旧 AI 和 Contact 实例、备份数据库并执行第 3 步，再更新 Contact、AI 和前端。
其余迁移脚本只执行一次。MySQL DDL 会自动提交，执行失败后应恢复备份并排查，不要直接重复执行。
新环境直接使用初始化 SQL，无需运行增量脚本。旧公共资源不会自动分配给用户。

测试环境若不需要迁移旧数据，可用 `config/sql/20261111-clean-obsolete-resources.sql` 替代上面的第 1–3 步，
直接删除公共资源、分享历史、旧凭据及废弃字段，保留个人资源和无关表；随后执行 PostgreSQL 的第 4 步。
该脚本适用于尚有 `scope`、`is_admin` 和旧 MCP 字段的数据库，不能在清理后的结构上重复执行。

### RAG 知识库

知识库原文件、提取正文和向量切片统一保存到独立 PostgreSQL，不再挂载共享知识目录。
所有知识库业务配置集中在 Nacos 的 `x-ai.yaml` / `knowledge` 节点，本地 `application.yml` 仅保留 Nacos 引导配置。
公开模板使用环境变量占位符；在私有 `.env` 配置 `KNOWLEDGE_VECTOR_JDBC_URL`、`KNOWLEDGE_VECTOR_USERNAME`、
`KNOWLEDGE_VECTOR_PASSWORD`、`KNOWLEDGE_EMBEDDING_BASE_URL`、`KNOWLEDGE_EMBEDDING_MODEL`
和 `KNOWLEDGE_EMBEDDING_DIMENSIONS`，由 Nacos 配置引用，Java 配置类不提供环境地址、模型或维度的默认值。
数据库与 Ollama 均须能从 AI 服务进程访问；容器部署时，
`127.0.0.1` 指容器本身，应改为宿主机或 Ollama 服务地址。

首次使用前，显式执行独立的 PostgreSQL 建表脚本，应用启动不会自动修改数据库：

```bash
PGPASSWORD="$KNOWLEDGE_VECTOR_PASSWORD" psql -h YOUR_DATABASE_HOST -p 5432 -U YOUR_DATABASE_USER -d YOUR_DATABASE_NAME \
  -f x-services/x-ai/src/main/resources/db/knowledge-pgvector-schema.sql
```

支持 TXT、Markdown、CSV、JSON、HTML、PDF 和 DOCX；文本文件使用 UTF-8，扫描 PDF 需要先 OCR。
上传请求完成解析、重叠切片和批量向量化，页面显示索引状态，支持正文/PDF 预览、切片分页、
语义检索及失败重试。原文默认上限 100 MB、正文上限 200 万 UTF-16 字符，切片默认 800 个
Unicode 字符、重叠 120 个字符，可在 `knowledge` 配置中调整。

每个用户自动拥有一个个人“公共知识库”，本人的所有会话默认检索该库，其他用户无法访问。
还可以创建多个独立知识库，并在新建或已有对话中额外关联一个或多个库；对话会保存关联选择。
文档按知识库隔离，同名文档可以分别存在于不同库中。公共知识库可以改名，不能删除；其他库删除后，
其文档和切片一并删除，已有对话后续检索自动忽略已删除的库。

每轮回答先从个人公共库与会话额外关联库中联合检索相关内容，`knowledge_search` 后续检索沿用同一范围，
返回知识库名称、文档来源、切片序号及相似度。页面试检索只查询当前打开的知识库。
覆盖、删除和重索引会同步使旧切片失效，索引版本校验阻止过期任务回写。
应用在索引过程中停止时，可在页面重新索引恢复。

已有环境升级多知识库前，停止旧 AI 实例。MySQL 执行
`config/sql/20261111-conversation-knowledge-bases.sql`，只补充会话关联字段，保留已有对话。
PostgreSQL 执行 `config/sql/20261111-reset-knowledge-postgresql.sql`，在同一事务中重建
`knowledge_base`、`knowledge_document` 和 `knowledge_chunk`，清空旧知识库目录、原文和向量切片，
不迁移旧资料；用户、会话及其他业务表保留。重置后个人公共库会在用户首次访问或对话时自动创建。
MySQL 增量脚本只执行一次，PostgreSQL 重置脚本每次执行都会清空知识库资料。
完成后启动新版 AI 和前端；新环境使用初始化 SQL 与上面的 PostgreSQL 建表脚本即可。

向量维度属于列和索引约束，不属于数据库。当前使用未固定维度的 `vector` 列，按模型名称及
维度过滤后计算余弦相似度，可在同一库保存不同维度的数据。换模型或维度后重新索引文档即可；
旧模型切片不会参与新模型查询。当前使用精确检索，数据量增大后可为固定模型/维度增加专用 HNSW 索引。

### AgentScope 2.0.4 与聊天用量

AgentScope 已从 2.0.1 升至 2.0.4。已有 MySQL 数据库先执行
`config/sql/20261109-agent-token-usage.sql`，为会话消息增加 `token_usage` 列；新库初始化 SQL 已包含该列。
聊天按本轮所有模型调用累计主 Agent 和子 Agent 的输入、输出、缓存命中/写入、推理、工具输入、
调用次数和模型累计耗时，流式更新并持久化。细分 token 已包含在输入或输出总量内，不能再次相加；
模型提供方未报告的细分项显示为 0，累计模型耗时也不等于并行任务的实际耗时。

本次升级同时获得 SSE 背压、并发上下文/工具/技能隔离、记忆后台 flush、子 Agent 恢复、
HTTP/1.1 兼容及 Docker 原生文件传输等修复，并适配新版空回复重试与 MCP 注册失败回收行为。
详细变化见 [官方发布说明](https://java.agentscope.io/v2/zh/docs/others/release-notes)，
统计口径见 [详细 token 指标说明](https://github.com/agentscope-ai/agentscope-java/pull/3215)。
项目已有会话控制、权限隔离文件发布和外部 API，因此此次保留这些实现，未额外接入 Service/Channel、
Responses API 或替换为 `AgentRun`，避免引入重复的执行和发布链路。

## 4. 构建与启动

```bash
mvn verify
# 原来的入口仍然有效：mvn -f x-parent/pom.xml verify
bash docker/sandbox/build-sandbox-image.sh
```

在 IDEA 中分别创建四个服务的 Spring Boot 或 Application 运行配置：

1. 打开 **Run → Edit Configurations**，选择服务运行配置。
2. 在 **Environment variables** 字段填写项目根目录 `.env` 的绝对路径，或点击该字段的 **Browse for .env files and scripts** 按钮选择文件。
3. 将 **Working directory** 设置为项目根目录，JDK 选择 21。
4. 四个运行配置均选择同一份 `.env`，然后直接在 IDEA 中运行或调试。

IDEA 会在每次启动时加载文件中的变量。具体字段位置见 [JetBrains 环境变量文档](https://www.jetbrains.com/help/idea/program-arguments-and-environment-variables.html)。如需限制单个服务内存，可在该运行配置的 **VM options** 填写 `-Xms64m -Xmx384m -XX:ActiveProcessorCount=2`。

本地 `.env` 建议保持 `SPRING_CLOUD_NACOS_DISCOVERY_IP=127.0.0.1`，使本机服务互相发现；其他主机无法通过该 IP 访问这些实例。`SPRING_QUARTZ_AUTO_STARTUP=false` 默认关闭本地任务调度，避免执行共享数据库中的已有任务；需要验证调度时再显式开启。同一服务并行运行多个实例时，为每个实例分配不同的 `ID_WORKER_ID`。

各服务的 Nacos 配置分别读取自己的端口变量，因此可以共用同一份 `.env`。已有项目占用默认端口时，在私有 `.env` 分别设置：

```dotenv
X_AGENT_GATEWAY_PORT=28081
X_AGENT_AUTH_PORT=28082
X_AGENT_CONTACT_PORT=28084
X_AGENT_AI_PORT=28086
AGENT_HTTP_API_URL=http://127.0.0.1:28081/ai/open/chat/run
AGENT_MCP_SERVER_URL=http://127.0.0.1:28081/mcp
```

前端的 `x-webs/x-ai-web/.env.local` 同步设置 `VITE_GATEWAY_URL=http://127.0.0.1:28081`。修改端口后重启对应服务；不要在这份公共环境文件中设置 `SERVER_PORT`，否则四个服务会争用同一个端口。

手动启动或部署时，在加载同一份环境变量后，分别运行各模块的 `target/<模块名>-0.1.0-SNAPSHOT.jar`。例如：

```bash
java -jar x-services/x-contact/target/x-contact-0.1.0-SNAPSHOT.jar
```

建议依次启动 Contact、Auth、AI、Gateway。

| 服务 | 默认端口 | 路径 |
| --- | --- | --- |
| x-gateway | 18081 | 工作台与外部统一入口 |
| x-auth | 18082 | `/auth` |
| x-contact | 18084 | `/contact` |
| x-ai | 18086 | `/ai`，含 `/ai/mcp` |

Gateway 将外部 `/mcp` 重写为 AI 服务的 `/ai/mcp`；AI 和 MCP 路由支持长请求和 SSE。部署时 Nacos 注册 IP、AI 端口应在 Gateway 网络中可达，AI 实例应能访问所配置的远程 MCP 地址；多实例应分配不同 `ID_WORKER_ID`。

知识库访问由 AI 服务连接 PostgreSQL 与 Ollama 完成，不再要求 Docker daemon 挂载知识目录。

## 5. 启动工作台

```bash
cd x-webs/x-ai-web
npm ci
npm run dev
```

访问 `http://localhost:5173`，使用初始化账号登录，添加自己的模型配置并设置默认模型。模型 API Key 保存在自己的数据库中，不属于 Nacos 公开模板。

开发代理默认指向 `http://127.0.0.1:18081`，可通过前端 `.env.local` 的 `VITE_GATEWAY_URL` 调整。生产部署见 [工作台说明](x-webs/x-ai-web/README.md)。

Open API：`POST /ai/open/chat/run`；MCP：`/mcp`。二者均使用工作台签发的 `X-API-Key`。`AGENT_HTTP_API_URL`、`AGENT_MCP_SERVER_URL` 控制工作台展示的实际外部接入地址。

## 验证与迁移说明

自动化检查、首次部署联调和发布边界见 [验证说明](docs/verification.md)。

```bash
mvn verify
cd x-webs/x-ai-web
npm test
npm run build
```

迁移保留 AI 完整功能及所需基础能力，移除了旧工作业务、聊天/群组/联系人社交、独立 WebSocket、MQ 框架、Seata 遗留配置、滑块验证码、旧数据库生成器及无消费者的工具类。保留工作台实际使用的图形验证码、失败锁定、Token/API Key/内部服务签名三条鉴权链路。

框架默认记录方法名、耗时和状态，避免将请求/响应体及认证头写入日志。原创代码采用 MIT；适配的第三方文件和依赖继续遵循各自许可证，详见 [第三方声明](THIRD_PARTY_NOTICES.md)。

原项目源文件和原 Nacos dev 配置保持独立。仓库只提供结构初始化，**不包含旧数据库业务数据或私有模型凭据**。已有数据迁移时，应单独备份并选择用户、AI、MCP、Skill 和 Quartz 相关表；不要导入旧群组/工作/聊天业务表。
