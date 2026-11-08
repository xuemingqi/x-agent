# X AI Web

独立的 Agent 平台 Web 前端，技术栈为 Vue 3、TypeScript、Vite 和 Element Plus。

## 环境要求

- Node.js 22.22.2+（22.x）、24.15+（24.x）或 26+，与测试环境依赖要求一致。
- npm 10 或更高版本。
- Gateway 默认运行在 `http://127.0.0.1:18081`。
- AI 接口路径为 `/ai`，登录与图形验证码接口路径为 `/auth`。

## 本地开发

进入 Web 模块并安装依赖：

```bash
cd x-webs/x-ai-web
npm ci
```

启动开发服务器：

```bash
npm run dev
```

浏览器访问 `http://localhost:5173`。开发环境会把 `/ai` 和 `/auth` 请求代理到
`http://127.0.0.1:18081`，因此需要同时启动 Gateway、AI、Auth、Contact 和 MCP Runtime 服务。

如果 Gateway 地址不同，在本模块 `.env.local` 中设置 `VITE_GATEWAY_URL`。

## 生产打包

```bash
cd x-webs/x-ai-web
npm ci
npm run build
```

打包完成后会生成 `x-ai-web/dist`。这个目录就是需要部署的静态文件，不需要把 Node.js 进程运行在生产服务器上。

## Nginx 部署示例

将 `dist` 上传到 `/opt/x-ai-web`，然后使用类似配置：

```nginx
server {
    listen 80;
    server_name agent.example.com;

    root /opt/x-ai-web;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /ai/ {
        proxy_pass http://127.0.0.1:18081/ai/;
        proxy_http_version 1.1;
        proxy_buffering off;
        proxy_read_timeout 600s;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    location /auth/ {
        proxy_pass http://127.0.0.1:18081/auth/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    location = /mcp {
        proxy_pass http://127.0.0.1:18081;
        proxy_http_version 1.1;
        proxy_buffering off;
        proxy_read_timeout 750s;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

`proxy_buffering off` 用于保证 SSE 聊天内容及时到达浏览器，`/mcp` 透传到 Gateway，认证和 MCP session 请求头按 Nginx 默认行为继续传递。

部署时，在后端环境变量中将 `AGENT_HTTP_API_URL` 和 `AGENT_MCP_SERVER_URL` 设置为用户实际可访问的域名地址，例如 `https://agent.example.com/ai/open/chat/run` 和 `https://agent.example.com/mcp`，供工作台展示外部接入信息。修改 Nginx 配置后执行：

```bash
nginx -t
nginx -s reload
```

## 常用命令

```bash
npm run dev        # 本地开发
npm test           # 运行前端单元测试
npm run typecheck  # TypeScript 类型检查
npm run build      # 生产打包
```
