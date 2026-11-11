# 验证说明

启动、IDEA 环境文件、Nacos 导入和空库初始化步骤见根目录 [README](../README.md)。自动化检查不连接个人在线服务，不需要仓库外的私有配置。

## 自动化检查

```bash
mvn --batch-mode clean verify
cd x-webs/x-ai-web
npm ci
npm test
npm run build
npm audit
```

后端测试包含内部请求签名、JWT 配置、异常响应、Agent 工具与会话逻辑、知识库隔离、MCP 直接 HTTP 连接及对外 MCP 的 HTTP 契约。前端测试包含登录、流式聊天、个人模型、Skill、知识库、MCP 和智能应用。

GitHub Actions 在干净环境中运行后端与前端验证，并核对生成的 Nacos ZIP、数据库初始化包与源文件一致。

2026-11-08 发布前检查：后端 176 项测试、前端 124 项测试、类型检查和生产构建通过；前端完整依赖审计为 0 项漏洞。空库 SQL 的 31 张表和管理员生成工具已在隔离的 MySQL 8.4 中验证。

2026-11-11 MCP 架构简化检查：后端 199 项测试、前端 151 项测试、类型检查和生产构建通过，初始化 SQL 与 Nacos ZIP 已重新生成并核对一致。真实 Servlet 测试覆盖 HTTP 直连、Header/Query 凭据、API Key 鉴权、用户隔离、业务会话和跨节点无状态请求。此次未执行已有数据库的增量迁移，也未部署服务。

2026-11-11 用户私有资源重构检查（移除分享前）：后端全量 `mvn verify` 通过（198 项测试），前端 143 项测试、类型检查和生产构建通过。
覆盖 MCP 所有者过滤、凭据脱敏、单一启用状态、同一份运行时快照、连接取消清理、知识库所有者查询与工具身份校验，
以及 Skill 分享接受后生成个人副本和同名冲突。初始化 SQL 与 Nacos ZIP 已重新生成并核对一致；初始普通用户 SQL 的密码摘要已验证。
MySQL/PostgreSQL 增量迁移已提供，未对现有数据库执行，也未部署服务。

2026-11-11 移除 Skill 分享检查：后端全量 `mvn clean verify` 通过（192 项测试），前端 141 项测试、类型检查和生产构建通过。
分享页面、接口、工具、业务服务和持久化模型已清除；AI 打包产物中不存在分享类或 Contact API 依赖。
初始化 SQL 已重新生成，共 25 张表，不再包含三张分享表。已有环境清理脚本见 `config/sql/20261111-remove-skill-sharing.sql`，
应在个人内容迁移完成后执行；此次未执行数据库迁移，也未部署服务。

2026-11-11 测试数据库直接清理：按用户要求执行 `config/sql/20261111-clean-obsolete-resources.sql`，
删除旧公共资源和凭据表、公共 MCP 数据、管理员与作用域字段以及旧 STDIO 字段，按用户归属重建索引。
PostgreSQL 公共文档清理与表注释更新也已执行。操作后复读确认当前 25 张业务及调度表均存在，
废弃表和字段不存在，索引列顺序与初始化结构一致，个人资源和无关表保留。此次未重启或部署服务。

## 首次部署后的联调

使用独立测试用户，按以下顺序检查：

1. Contact、Auth、AI、Gateway 四个服务启动并在相同 Nacos namespace/group 中注册，实例 IP 和端口在调用方网络中可达。
2. 通过 Gateway 获取验证码、登录、校验 Token，再查询会话和 MCP 列表，覆盖 Auth、Contact、AI 和内部签名。添加 Streamable HTTP MCP，验证连接、工具发现、启用状态和用户资源隔离。
3. 添加自己的模型并设为默认，签发 API Key，调用 `POST /ai/open/chat/run`。
4. 通过 Gateway 的 `/mcp` 完成 initialize、tools/list 和 run_agent，确认返回真实模型结果；多节点时验证请求可分发到不同 AI 实例。
5. 验证 MinIO 对象读写与 Docker 沙箱。预先创建宿主机挂载目录，确保 Docker daemon 可访问；Colima 等共享文件系统尤其需要此步骤。
6. 确认未登录请求和无效 API Key 被拒绝。升级环境确认两个旧 MCP 服务已停止，旧配置和数据库升级步骤已完成。删除本次测试数据和凭据。

本项目曾使用实际 Nacos、MySQL、Redis、MinIO、Docker 及真实模型完成上述 HTTP/MCP 联调。模型供应商、多节点部署、定时任务与生产负载仍需在各自环境中验证；测试通过不等于完成全面安全审计。

## 发布边界

- 发布 MIT 原创代码、第三方许可说明、无业务数据的 SQL 和占位符 Nacos 模板。
- `.env`、前端 `.env.local`、`.local`、运行数据、日志、IDE 设置及构建产物不入库。
- 个人在线地址、密码、密钥、Token 和模型配置不写入公开配置或 Git 历史。
