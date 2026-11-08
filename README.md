# X Agent

基于 Java 21、Spring Boot 4、Spring Cloud Alibaba 和 AgentScope Java 的独立 AI Agent 平台，采用 [MIT 协议](LICENSE)。

支持多用户登录、个人模型配置、流式对话、会话与文件、Docker 沙箱、公共/个人 Skill、知识库、MCP 管理、定时任务、浏览器截图、多模态和智能应用，并通过 HTTP Open API 与 MCP 对外提供 Agent 能力。

## 项目结构

```text
x-parent                 父依赖、版本和构建管理
x-apis                   服务间 Feign 契约
  x-auth-api             Token 校验
  x-contact-api          用户身份与账号
  x-ai-api               AI / Open API
  x-ai-mcp-runtime-api   MCP 管理契约
x-framework              common / core / web / db / redis / minio
x-services
  x-auth                 登录、验证码、JWT、退出及失败锁定
  x-contact              用户认证、身份查询、账号创建
  x-gateway              路由、追踪、文档及 Sentinel 规则
  x-ai                   AgentScope、模型、会话、知识、Skill、任务
  x-ai-mcp-runtime       内部 MCP 配置、凭证与连接运行时
  x-ai-mcp-gateway       对外 Streamable HTTP MCP 服务
x-webs/x-ai-web           Vue 3 + TypeScript 工作台
config/nacos             可直接导入的公开 Nacos 配置
config/x-agent-nacos.zip  Nacos 控制台原生 ZIP 导入包
config/sql               空数据库初始化 SQL
scripts                  配置导入、SQL 与初始管理员工具
```

根 `pom.xml` 仅增加统一构建入口，继续保留 `x-parent / x-apis / x-framework / x-services` 分层。项目坐标版本为 `0.1.0-SNAPSHOT`，避免与旧项目产物混用。

## 环境

- JDK 21、Maven 3.9+
- Node.js 22.22.2+（22.x LTS）、npm（仅前端构建需要）
- Nacos 2.x（本项目验证版本为 2.4.2）
- MySQL 8、Redis、MinIO
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

共 12 项：`DEFAULT_GROUP` 下 1 份公共配置、6 份服务配置，`SENTINEL_GROUP` 下 5 份规则。规则初始为空，可按部署需要配置。重复导入默认跳过相同内容；发现不同内容会先停止。确需更新时检查差异后使用 `--overwrite`。

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

生成首个管理员的 INSERT（交互输入密码，不会输出明文密码）：

```bash
umask 077
python3 scripts/create-admin-sql.py --mobile YOUR_LOGIN_ID --name Administrator > /tmp/x-agent-admin.sql
mysql -h 127.0.0.1 -u root -p x_agent < /tmp/x-agent-admin.sql
rm /tmp/x-agent-admin.sql
```

`--mobile` 是登录标识，与登录页面填写的值一致。默认初始 ID 为 1；重复执行不会覆盖已有账号。后续可通过 `/contact/user/create` 创建普通用户；管理员由数据库 `is_admin` 字段管理。

可选的演示 Skill 种子位于 `x-services/x-ai/src/main/resources/db/skill-seed.sql`。改动实体结构后，可运行 `python3 scripts/build-init-sql.py` 重新汇总初始化包。

## 4. 构建与启动

```bash
mvn verify
# 原来的入口仍然有效：mvn -f x-parent/pom.xml verify
bash docker/sandbox/build-sandbox-image.sh
```

在 IDEA 中分别创建六个服务的 Spring Boot 或 Application 运行配置：

1. 打开 **Run → Edit Configurations**，选择服务运行配置。
2. 在 **Environment variables** 字段填写项目根目录 `.env` 的绝对路径，或点击该字段的 **Browse for .env files and scripts** 按钮选择文件。
3. 将 **Working directory** 设置为项目根目录，JDK 选择 21。
4. 六个运行配置均选择同一份 `.env`，然后直接在 IDEA 中运行或调试。

IDEA 会在每次启动时加载文件中的变量。具体字段位置见 [JetBrains 环境变量文档](https://www.jetbrains.com/help/idea/program-arguments-and-environment-variables.html)。如需限制单个服务内存，可在该运行配置的 **VM options** 填写 `-Xms64m -Xmx384m -XX:ActiveProcessorCount=2`。

本地 `.env` 建议保持 `SPRING_CLOUD_NACOS_DISCOVERY_IP=127.0.0.1`，使本机服务互相发现；其他主机无法通过该 IP 访问这些实例。`SPRING_QUARTZ_AUTO_STARTUP=false` 默认关闭本地任务调度，避免执行共享数据库中的已有任务；需要验证调度时再显式开启。同一服务并行运行多个实例时，为每个实例分配不同的 `ID_WORKER_ID`。

各服务的 Nacos 配置分别读取自己的端口变量，因此可以共用同一份 `.env`。已有项目占用默认端口时，在私有 `.env` 分别设置：

```dotenv
X_AGENT_GATEWAY_PORT=28081
X_AGENT_AUTH_PORT=28082
X_AGENT_CONTACT_PORT=28084
X_AGENT_AI_PORT=28086
X_AGENT_MCP_RUNTIME_PORT=28087
X_AGENT_MCP_GATEWAY_PORT=28088
AGENT_HTTP_API_URL=http://127.0.0.1:28081/ai/open/chat/run
AGENT_MCP_SERVER_URL=http://127.0.0.1:28081/mcp
```

前端的 `x-webs/x-ai-web/.env.local` 同步设置 `VITE_GATEWAY_URL=http://127.0.0.1:28081`。修改端口后重启对应服务；不要在这份公共环境文件中设置 `SERVER_PORT`，否则六个服务会争用同一个端口。

手动启动或部署时，在加载同一份环境变量后，分别运行各模块的 `target/<模块名>-0.1.0-SNAPSHOT.jar`。例如：

```bash
java -jar x-services/x-contact/target/x-contact-0.1.0-SNAPSHOT.jar
```

建议依次启动 Contact、Auth、MCP Runtime、AI、MCP Gateway、Gateway。

| 服务 | 默认端口 | 路径 |
| --- | --- | --- |
| x-gateway | 18081 | 工作台与外部统一入口 |
| x-auth | 18082 | `/auth` |
| x-contact | 18084 | `/contact` |
| x-ai | 18086 | `/ai` |
| x-ai-mcp-runtime | 18087 | `/mcp`，内部使用 |
| x-ai-mcp-gateway | 18088 | `/mcp` |

Gateway 将 `/mcp` 转发到 **x-ai-mcp-gateway**；MCP Runtime 通过 Nacos 被 AI 服务发现，不由公网网关暴露。AI 和 MCP 路由支持长请求和 SSE。部署时 Nacos 注册 IP、Runtime 端口应在调用方网络中可达；多实例应分配不同 `ID_WORKER_ID`。

沙箱共享目录需要能被 Docker daemon 挂载。远程 Docker 或多节点部署时，为 `AGENT_PUBLIC_KNOWLEDGE_DIRECTORY`、`AGENT_USER_WORKSPACE_DIRECTORY` 设置对应宿主机的绝对共享路径。

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
