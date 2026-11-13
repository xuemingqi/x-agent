import { isAgentAppDataKey } from '@/types/agent-app';
import type { AgentAppContent, AgentAppOperation, AgentAppPermission } from '@/types/agent-app';

const PROTOCOL = 'x-agent-app';
const MAX_REQUEST_BYTES = 256 * 1024;
const MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
const MAX_CONCURRENT_REQUESTS = 4;
const MAX_CONCURRENT_HTTP_REQUESTS = 32;
const MAX_SESSION_REQUESTS = 4096;
const MAX_SESSION_HTTP_REQUESTS = 16384;
const OPERATIONS: AgentAppOperation[] = ['model.generate', 'agent.run', 'http.request', 'storage.get', 'storage.set', 'storage.remove'];

export interface ApplicationBridgeOptions {
  frame: () => Window | null;
  channel: string;
  permissions: AgentAppPermission[];
  execute: (operation: AgentAppOperation, payload: Record<string, unknown>, signal: AbortSignal) => Promise<unknown>;
  onError: (message: string) => void;
  onReady: () => void;
}

/** 为消息通道和 CSP 生成不可预测的标识，兼容普通 HTTP 下没有 randomUUID 的浏览器。 */
export function createApplicationToken(): string {
  return Array.from(crypto.getRandomValues(new Uint8Array(16)), value => value.toString(16).padStart(2, '0')).join('');
}

/** 隔离页面没有登录凭据；只有来自当前 iframe 的有界 SDK 请求才进入后端。 */
export function createApplicationBridge(options: ApplicationBridgeOptions): () => void {
  const controllers = new Map<string, AbortController>();
  const httpRequests = new Set<string>();
  const seen = new Set<string>();
  let disposed = false;
  let port: MessagePort | undefined;
  let sessionRequests = 0;
  let sessionHttpRequests = 0;
  function reply(requestId: string, result: Record<string, unknown>): void {
    if (disposed) return;
    port?.postMessage({ protocol: PROTOCOL, sdkVersion: 1, channel: options.channel,
      type: 'response', requestId, ...result });
  }
  async function receive(event: MessageEvent): Promise<void> {
    if (disposed) return;
    const message = event.data;
    if (!message || message.protocol !== PROTOCOL || message.sdkVersion !== 1 || message.channel !== options.channel) return;
    if (message.type === 'error') {
      options.onError(typeof message.error === 'string' ? message.error.slice(0, 4096) : '应用运行失败');
      return;
    }
    const requestId = message.requestId;
    if (typeof requestId !== 'string' || !/^[\w-]{1,80}$/.test(requestId)) return;
    if (message.type === 'cancel') { controllers.get(requestId)?.abort(); return; }
    if (message.type !== 'request' || seen.has(requestId)) return;
    const operation = message.operation as AgentAppOperation;
    const isHttp = operation === 'http.request';
    if (isHttp ? sessionHttpRequests >= MAX_SESSION_HTTP_REQUESTS : sessionRequests >= MAX_SESSION_REQUESTS) {
      reply(requestId, { error: '本次运行调用过多，请重新运行应用' }); return;
    }
    seen.add(requestId);
    if (isHttp) sessionHttpRequests++;
    else sessionRequests++;
    const permission = typeof operation === 'string' && operation.startsWith('storage.') ? 'storage' : operation;
    if (!OPERATIONS.includes(operation) || !options.permissions.includes(permission as AgentAppPermission)) {
      reply(requestId, { error: '应用未声明此项能力' }); return;
    }
    try {
      if (!message.payload || typeof message.payload !== 'object' || Array.isArray(message.payload)) {
        throw new Error('SDK 请求格式无效');
      }
      if (new TextEncoder().encode(JSON.stringify(message.payload)).length > MAX_REQUEST_BYTES) {
        throw new Error('SDK 请求超过 256 KB');
      }
      if (operation.startsWith('storage.') && !isAgentAppDataKey(message.payload.key)) {
        throw new Error('数据键需为 1–128 位字母、数字、点、下划线或连字符，且不能为 . 或 ..');
      }
      const concurrent = isHttp ? httpRequests.size : controllers.size - httpRequests.size;
      if (concurrent >= (isHttp ? MAX_CONCURRENT_HTTP_REQUESTS : MAX_CONCURRENT_REQUESTS)) {
        throw new Error('同时执行的请求过多，请稍后重试');
      }
      const controller = new AbortController();
      controllers.set(requestId, controller);
      if (isHttp) httpRequests.add(requestId);
      const value = await options.execute(operation, message.payload, controller.signal);
      if (new TextEncoder().encode(JSON.stringify(value ?? null)).length > MAX_RESPONSE_BYTES) {
        throw new Error('SDK 返回内容过大');
      }
      reply(requestId, { value });
    } catch (cause) {
      reply(requestId, { error: cause instanceof Error ? cause.message : '应用能力调用失败' });
    } finally {
      controllers.delete(requestId);
      httpRequests.delete(requestId);
    }
  }
  function initialize(event: MessageEvent): void {
    const message = event.data;
    if (disposed || port || !options.frame() || event.source !== options.frame() || !message
      || message.protocol !== PROTOCOL || message.sdkVersion !== 1 || message.channel !== options.channel
      || message.type !== 'ready' || event.ports.length !== 1) return;
    // port 由初始文档创建并转交；后续导航即使复用 WindowProxy 也无法取得能力通道。
    port = event.ports[0]!;
    port.onmessage = receive;
    port.start();
    port.postMessage({ protocol: PROTOCOL, sdkVersion: 1, channel: options.channel, type: 'initialize' });
    options.onReady();
  }
  window.addEventListener('message', initialize);
  return () => {
    disposed = true;
    window.removeEventListener('message', initialize);
    if (port) { port.onmessage = null; port.close(); }
    controllers.forEach(controller => controller.abort());
    controllers.clear();
    httpRequests.clear();
  };
}

/** 内容在 bootstrap 执行后插入；源码与样式不能通过闭合 script 标签改变隔离文档。 */
export function buildApplicationDocument(content: AgentAppContent, javascript: string, channel: string): string {
  const document = new DOMParser().parseFromString(content.html, 'text/html');
  document.querySelectorAll('script, style, link, meta, base, iframe, object, embed').forEach(element => element.remove());
  document.body.querySelectorAll('*').forEach(element => {
    for (const attribute of [...element.attributes]) {
      if (/^on/i.test(attribute.name) || ['srcdoc', 'nonce'].includes(attribute.name)
        || (['href', 'xlink:href', 'action', 'formaction', 'src'].includes(attribute.name)
          && /^\s*javascript:/i.test(attribute.value))) element.removeAttribute(attribute.name);
    }
  });
  const payload = { html: document.body.innerHTML, css: content.css, javascript, channel };
  const encoded = bytesToBase64(new TextEncoder().encode(JSON.stringify(payload)));
  const nonce = createApplicationToken();
  const policy = `default-src 'none'; script-src 'nonce-${nonce}'; style-src 'unsafe-inline'; `
    + "img-src data: blob:; font-src data:; connect-src 'none'; frame-src 'none'; worker-src 'none'; "
    + "object-src 'none'; base-uri 'none'; form-action 'none'";
  return `<!doctype html><html><head><meta http-equiv="Content-Security-Policy" content="${policy}">`
    + '<meta name="referrer" content="no-referrer"><meta name="viewport" content="width=device-width, initial-scale=1">'
    + '<style>body{margin:0;font:14px/1.6 system-ui,sans-serif;color:#242424}*{box-sizing:border-box}</style>'
    + `</head><body><script nonce="${nonce}">(${applicationBootstrap.toString()})('${encoded}');</script></body></html>`;
}

function bytesToBase64(bytes: Uint8Array): string {
  const parts: string[] = [];
  for (let offset = 0; offset < bytes.length; offset += 8192) {
    parts.push(String.fromCharCode(...bytes.subarray(offset, offset + 8192)));
  }
  return btoa(parts.join(''));
}

/** 此函数会序列化到隔离文档，必须保持自包含，不捕获宿主变量。 */
function applicationBootstrap(encoded: string): void {
  const payload = JSON.parse(new TextDecoder().decode(Uint8Array.from(atob(encoded), character => character.charCodeAt(0))));
  const nonce = (document.currentScript as HTMLScriptElement | null)?.nonce ?? '';
  const pending = new Map<string, { resolve: (value: unknown) => void; reject: (error: Error) => void;
    dispose: () => void }>();
  let sequence = 0;
  const channel = new MessageChannel();
  let initialized = false;
  const send = (message: Record<string, unknown>) => channel.port1.postMessage({ protocol: 'x-agent-app', sdkVersion: 1,
    channel: payload.channel, ...message });
  const call = (operation: string, data: unknown, signal?: AbortSignal) => new Promise((resolve, reject) => {
    if (signal?.aborted) { reject(new DOMException('应用请求已取消', 'AbortError')); return; }
    const requestId = String(++sequence);
    const dispose = () => { clearTimeout(timer); signal?.removeEventListener('abort', abort); };
    const abort = () => {
      pending.delete(requestId);
      dispose();
      send({ type: 'cancel', requestId });
      reject(new DOMException('应用请求已取消', 'AbortError'));
    };
    const timer = setTimeout(() => {
      pending.delete(requestId);
      dispose();
      send({ type: 'cancel', requestId });
      reject(new Error('应用请求超时，请稍后重试'));
    }, 180000);
    pending.set(requestId, { resolve, reject, dispose });
    signal?.addEventListener('abort', abort, { once: true });
    try { send({ type: 'request', requestId, operation, payload: data }); }
    catch (error) { pending.delete(requestId); dispose(); reject(error); }
  });
  channel.port1.onmessage = event => {
    const message = event.data;
    if (!message || message.protocol !== 'x-agent-app' || message.sdkVersion !== 1 || message.channel !== payload.channel) return;
    if (message.type === 'initialize' && !initialized) { initialized = true; run(); return; }
    if (message.type !== 'response') return;
    const request = pending.get(message.requestId);
    if (!request) return;
    pending.delete(message.requestId);
    request.dispose();
    if (typeof message.error === 'string') request.reject(new Error(message.error));
    else request.resolve(message.value);
  };
  channel.port1.start();
  const report = (error: unknown) => send({ type: 'error', error: error instanceof Error ? error.message : String(error) });
  window.addEventListener('error', event => report(event.error || event.message));
  window.addEventListener('unhandledrejection', event => report(event.reason));
  window.addEventListener('pagehide', () => {
    pending.forEach(request => { request.dispose(); request.reject(new Error('应用已关闭')); });
    pending.clear();
    channel.port1.close();
  });
  Object.defineProperty(window, 'app', { value: Object.freeze({
    ai: Object.freeze({ generate: (data: unknown) => call('model.generate', data) }),
    agent: Object.freeze({ run: (data: unknown) => call('agent.run', data) }),
    http: Object.freeze({ request: (data: unknown, options?: { signal?: AbortSignal }) => call('http.request', data, options?.signal) }),
    storage: Object.freeze({
      get: (key: string) => call('storage.get', { key }),
      set: (key: string, value: unknown) => call('storage.set', { key, value }),
      remove: (key: string) => call('storage.remove', { key }),
    }),
  }) });
  const style = document.createElement('style');
  style.textContent = payload.css;
  document.head.append(style);
  const root = document.createElement('main');
  root.innerHTML = payload.html;
  document.body.append(root);
  function run(): void {
    const script = document.createElement('script');
    script.setAttribute('nonce', nonce);
    script.textContent = payload.javascript;
    document.body.append(script);
  }
  parent.postMessage({ protocol: 'x-agent-app', sdkVersion: 1, channel: payload.channel, type: 'ready' }, '*', [channel.port2]);
}
