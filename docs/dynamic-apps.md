# 动态智能应用

智能应用中心只展示当前用户的个人应用，应用创建在普通对话中完成。应用使用 HTML 静态片段、CSS 和自包含 TypeScript，
浏览器按需加载 TypeScript 编译器，将源码转换成 JavaScript 后放入隔离 iframe 运行。
新增应用无需新增 Java 类、Vue 页面或重新发布主系统。

## 存储与升级

完整应用包按版本写入当前配置的私有 MinIO bucket。MySQL 保存应用所有者、目录信息、当前版本、
对象引用、SHA-256 和字节数，不保存应用代码大字段，也不向浏览器暴露 MinIO 凭据或任意对象下载地址。
应用数据与代码版本分开，恢复历史代码版本不会恢复或清空数据。

已有环境部署新版 AI 服务前，在备份后的 MySQL 执行一次：

```bash
mysql -h YOUR_DATABASE_HOST -u YOUR_DATABASE_USER -p YOUR_DATABASE_NAME < config/sql/20261112-dynamic-agent-apps.sql
```

新环境使用根目录初始化 SQL，应用启动不自动建表。MinIO 沿用现有配置，无需单独部署代码执行服务。

每次保存先上传新的不可变对象，再在短数据库事务中发布版本指针；明确回滚时清理新上传的对象。
事务提交或回滚结果不明时保留对象并记录恢复线索，避免误删数据库可能已经引用的版本。
更新和恢复历史版本必须携带读取时的 `baseVersion`，版本冲突后应重新读取，不能直接覆盖。
删除会清除应用数据、历史版本和对应 MinIO 对象；对象删除失败时保留不可见记录及对象引用，
可对相同应用 ID 重试删除。

## 创建与修改

在普通对话中向 Agent 描述应用需求即可创建；智能应用列表页不提供创建或模板入口。
Agent 的 `app_create`、`app_get`、`app_update`、`app_list`、`app_versions`、`app_activate_version` 和 `app_delete`
工具均以平台注入的当前用户身份操作。已有应用的删除、编辑和版本记录入口位于列表卡片右下角，
鼠标悬停或键盘聚焦卡片时显示；触屏设备直接显示。编辑和版本记录分别打开对应管理面板，
支持保存新版本或恢复历史版本，运行页不再展示这些管理入口。
原配色实验室与 HTTP 请求实验室已迁移为 `agent-app-templates` 下的 TS 模板，
Agent 可按对话中的需求安装模板，完整代码按普通应用包发布到 MinIO，得到独立的个人应用 ID。
模板副本与 Agent 生成的应用走相同的加载、权限、存储、编辑和版本恢复流程，可创建多个互不影响的副本。
原 Java 应用定义、应用专用 Service、专用接口和专用 Vue 页面已移除。
Agent 也可以通过 `app_template_list`、`app_template_install` 查找模板并创建个人副本。

应用 `html` 是 body 内的静态片段，可自行声明元素 ID；交互写在 `source` 中。
`source` 自动放入异步函数，支持顶层 `await`。不支持 import/export、require、第三方包、CDN、
直接网络请求和在 HTML 中嵌入脚本或事件。网络请求通过声明权限后的 SDK 转发；图片使用内嵌 data URL。
第一版编译执行语法转换和模块入口检查，不提供完整的 TypeScript 类型检查。
保存成功代表文件已持久化；应在应用页面实际运行后验证交互和模型结果。

## SDK v1

应用通过全局 `window.app`（或 `app`）调用宿主提供的异步 SDK，不能读取宿主登录凭据和模型 Key。
权限随应用版本保存，前端消息桥和后端均检查能力与当前生效版本；旧页面在版本变化后应重新加载。

| 方法 | 权限 | 结果 |
| --- | --- | --- |
| `app.ai.generate({prompt, systemPrompt?, modelId?})` | `model.generate` | `{text}` |
| `app.agent.run({request, modelId?})` | `agent.run` | `{text, sessionId}` |
| `app.storage.get(key)` | `storage` | JSON 值，不存在时为 `null` |
| `app.storage.set(key, value)` | `storage` | 是否成功 |
| `app.storage.remove(key)` | `storage` | 是否成功 |
| `app.http.request(request, {signal?})` | `http.request` | `{url,statusCode,headers,body,elapsedMs,responseBytes,truncated,error}` |

省略 `modelId` 使用当前用户的默认模型；指定时必须传该用户拥有的模型 ID 字符串。
应用数据键支持 1–128 位 ASCII 字母、数字、点、下划线和连字符，禁止精确的 `.` 和 `..`。
单个数据值最大 1 MiB，SDK 请求载荷最大 256 KiB，适合保存小型 JSON 状态。
完整应用包最大 4 MiB，保存在 MinIO；第一版 SDK 不提供独立的大文件上传接口。
`agent.run` 是一次独立任务，只注册当前用户启用的远程 MCP 工具，不继承聊天 Agent 的应用、Skill、
定时任务等管理工具或文件系统。应用声明该权限意味着其任务可调用这些已启用的 MCP 工具。
运行中的应用没有自动后台驻留能力；页面关闭后仍需持续执行的任务应使用平台后台任务。

`http.request` 使用通用 HTTP 传输，不继承平台登录头或 Cookie，不自动跟随重定向或重试。
单次请求包含 method、url、query、headers、body、timeoutMs；query/headers 每项为 `{name,value,enabled}`。
HTTP 传输每次最多读取 256 KiB 响应正文，SDK 普通能力最多同时执行 4 个请求，HTTP 单独最多 32 个。
可选 AbortSignal 只在浏览器侧取消请求，不进入序列化载荷。
HTTP 实验室的调度、限速、分位数和 AI 分析规则均在 TS 中，最多 20 并发、100 次/秒、120 秒；
离开页面会终止本轮调度。延迟取通用 HTTP 传输返回的服务端实测耗时，不含 SDK 浏览器往返；
吞吐率按浏览器整轮实际计时计算，SDK 故障和取消请求不纳入延迟样本。

例如，页面片段可以是：

```html
<label for="notes">会议记录</label>
<textarea id="notes"></textarea>
<button id="summarize" type="button">整理纪要</button>
<pre id="result" role="status"></pre>
```

对应的 TypeScript（声明 `model.generate` 权限）：

```ts
const input = document.querySelector<HTMLTextAreaElement>('#notes')!;
const button = document.querySelector<HTMLButtonElement>('#summarize')!;
const result = document.querySelector<HTMLElement>('#result')!;
button.addEventListener('click', async () => {
  if (!input.value.trim()) return;
  button.disabled = true;
  result.textContent = '正在整理…';
  try {
    const response = await app.ai.generate({
      systemPrompt: '将会议记录整理为议题、结论和行动项，不补充记录以外的事实。',
      prompt: input.value,
    });
    result.textContent = response.text;
  } catch (error) {
    result.textContent = error instanceof Error ? error.message : '整理失败，请重试';
  } finally {
    button.disabled = false;
  }
});
```

iframe 使用独立的不透明来源与严格 CSP，SDK 校验消息来源、通道、请求标识、载荷和并发数。
这一机制限制脚本访问宿主和直接加载外部资源，不应当作对任意浏览器代码的完整网络隔离。
后端接口独立校验用户归属、权限与版本，不信任应用自行上报的身份。
