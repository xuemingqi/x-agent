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

后端测试包含内部请求签名、JWT 配置、异常响应、Agent 工具与会话逻辑、知识库隔离，以及 MCP Gateway 的实际 HTTP/Feign 契约。前端测试包含登录、流式聊天、个人模型、Skill、知识库、MCP 和智能应用。

GitHub Actions 在干净环境中运行后端与前端验证，并核对生成的 Nacos ZIP、数据库初始化包与源文件一致。

2026-11-08 发布前检查：后端 176 项测试、前端 124 项测试、类型检查和生产构建通过；前端完整依赖审计为 0 项漏洞。空库 SQL 的 31 张表和管理员生成工具已在隔离的 MySQL 8.4 中验证。

## 首次部署后的联调

使用独立测试用户，按以下顺序检查：

1. 六个服务启动并在相同 Nacos namespace/group 中注册，实例 IP 和端口在调用方网络中可达。
2. 通过 Gateway 获取验证码、登录、校验 Token，再查询管理员身份、会话和 MCP 列表，覆盖 Auth、Contact、AI、Runtime 和内部签名。
3. 添加自己的模型并设为默认，签发 API Key，调用 `POST /ai/open/chat/run`。
4. 通过 `/mcp` 完成 initialize、tools/list 和 run_agent，确认返回真实模型结果。
5. 验证 MinIO 对象读写与 Docker 沙箱。预先创建宿主机挂载目录，确保 Docker daemon 可访问；Colima 等共享文件系统尤其需要此步骤。
6. 确认未登录请求和无效 API Key 被拒绝，内部 MCP Runtime 不可由公开网关直接访问。删除本次测试数据和凭据。

本项目曾使用实际 Nacos、MySQL、Redis、MinIO、Docker 及真实模型完成上述 HTTP/MCP 联调。模型供应商、多节点部署、定时任务与生产负载仍需在各自环境中验证；测试通过不等于完成全面安全审计。

## 发布边界

- 发布 MIT 原创代码、第三方许可说明、无业务数据的 SQL 和占位符 Nacos 模板。
- `.env`、前端 `.env.local`、`.local`、运行数据、日志、IDE 设置及构建产物不入库。
- 个人在线地址、密码、密钥、Token 和模型配置不写入公开配置或 Git 历史。
