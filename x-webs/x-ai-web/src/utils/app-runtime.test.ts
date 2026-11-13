// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { AgentAppContent } from '@/types/agent-app';
import { buildApplicationDocument, createApplicationBridge } from './app-runtime';

const cleanups: (() => void)[] = [];
afterEach(() => { cleanups.splice(0).forEach(cleanup => cleanup()); vi.restoreAllMocks(); });

function fakePort() {
  return { postMessage: vi.fn(), start: vi.fn(), close: vi.fn(), onmessage: null as ((event: MessageEvent) => void) | null };
}

function setup(permissions: AgentAppContent['permissions'] = ['storage']) {
  const source = {} as Window;
  const port = fakePort();
  const execute = vi.fn<Parameters<typeof createApplicationBridge>[0]['execute']>().mockResolvedValue({ saved: true });
  const onError = vi.fn();
  const onReady = vi.fn();
  cleanups.push(createApplicationBridge({ frame: () => source, channel: 'channel-1', permissions, execute, onError, onReady }));
  const envelope = { protocol: 'x-agent-app', sdkVersion: 1, channel: 'channel-1' };
  function initialize(extra: Record<string, unknown> = {}, originSource = source) {
    window.dispatchEvent(new MessageEvent('message', { source: originSource, ports: [port as unknown as MessagePort],
      data: { ...envelope, type: 'ready', ...extra } }));
  }
  function dispatch(data: Record<string, unknown>) {
    port.onmessage?.(new MessageEvent('message', { data: { ...envelope, ...data } }));
  }
  return { source, port, execute, onError, onReady, initialize, dispatch };
}

function request(requestId = '1', operation = 'storage.get', payload: unknown = { key: 'notes' }) {
  return { type: 'request', requestId, operation, payload };
}

describe('application SDK boundary', () => {
  it('accepts only the current frame and matching channel/protocol', async () => {
    const { source, port, execute, initialize, dispatch, onReady } = setup();
    initialize({}, {} as Window);
    initialize({ channel: 'other' });
    initialize({ sdkVersion: 2 });
    initialize({ protocol: 'other' });
    expect(onReady).not.toHaveBeenCalled();
    initialize();
    expect(onReady).toHaveBeenCalledOnce();
    port.postMessage.mockClear();
    window.dispatchEvent(new MessageEvent('message', { source, data: { protocol: 'x-agent-app', sdkVersion: 1, channel: 'channel-1', ...request() } }));
    expect(execute).not.toHaveBeenCalled();
    dispatch(request());
    await vi.waitFor(() => expect(port.postMessage).toHaveBeenCalledWith(expect.objectContaining({
      type: 'response', requestId: '1', value: { saved: true },
    })));
    expect(execute).toHaveBeenCalledOnce();
    expect(execute).toHaveBeenCalledWith('storage.get', { key: 'notes' }, expect.any(AbortSignal));
    dispatch(request());
    expect(execute).toHaveBeenCalledOnce();
  });

  it('denies undeclared capabilities and malformed storage keys', async () => {
    const { port, execute, initialize, dispatch } = setup();
    initialize();
    port.postMessage.mockClear();
    dispatch(request('1', 'agent.run', { request: 'perform task' }));
    dispatch(request('2', 'storage.get', { key: '../other' }));
    dispatch(request('3', 'storage.get', []));
    dispatch(request('4', 'storage.set', { key: 'notes', value: 'x'.repeat(256 * 1024) }));
    expect(execute).not.toHaveBeenCalled();
    expect(port.postMessage).toHaveBeenCalledTimes(4);
    expect(vi.mocked(port.postMessage).mock.calls[0]?.[0]).toMatchObject({ error: '应用未声明此项能力' });
  });

  it('permits underscore-prefixed keys while blocking path-normalizing dot keys', async () => {
    const { execute, initialize, dispatch } = setup();
    initialize();
    dispatch(request('1', 'storage.get', { key: '_state' }));
    dispatch(request('2', 'storage.get', { key: '.state' }));
    dispatch(request('3', 'storage.remove', { key: '..' }));
    dispatch(request('4', 'storage.remove', { key: '.' }));
    expect(execute).toHaveBeenCalledTimes(2);
    expect(execute.mock.calls.map(call => call[1].key)).toEqual(['_state', '.state']);
  });

  it('limits concurrent calls and aborts in-flight work when disposed', async () => {
    const { port, execute, initialize, dispatch } = setup();
    initialize();
    port.postMessage.mockClear();
    execute.mockImplementation(() => new Promise(() => undefined));
    for (let i = 1; i <= 5; i++) dispatch(request(String(i)));
    expect(execute).toHaveBeenCalledTimes(4);
    expect(port.postMessage).toHaveBeenCalledWith(expect.objectContaining({ requestId: '5', error: expect.stringContaining('过多') }));
    dispatch({ type: 'cancel', requestId: '1' });
    expect(execute.mock.calls[0]?.[2].aborted).toBe(true);
    cleanups.splice(0).forEach(cleanup => cleanup());
    expect(execute.mock.calls.every(call => call[2].aborted)).toBe(true);
    dispatch(request('6'));
    expect(execute).toHaveBeenCalledTimes(4);
  });

  it('isolates the HTTP concurrency budget from model calls and rejects undeclared HTTP access', () => {
    const blocked = setup();
    blocked.initialize();
    blocked.dispatch(request('denied', 'http.request', { url: 'https://example.test' }));
    expect(blocked.execute).not.toHaveBeenCalled();
    expect(blocked.port.postMessage).toHaveBeenCalledWith(expect.objectContaining({ error: '应用未声明此项能力' }));
    cleanups.splice(0).forEach(cleanup => cleanup());

    const { execute, port, initialize, dispatch } = setup(['http.request', 'model.generate']);
    initialize();
    execute.mockImplementation(() => new Promise(() => undefined));
    for (let index = 1; index <= 33; index++) dispatch(request(`http-${index}`, 'http.request', { url: 'https://example.test' }));
    for (let index = 1; index <= 5; index++) dispatch(request(`model-${index}`, 'model.generate', { prompt: '总结' }));
    expect(execute).toHaveBeenCalledTimes(36);
    expect(port.postMessage).toHaveBeenCalledWith(expect.objectContaining({ requestId: 'http-33', error: expect.stringContaining('过多') }));
    expect(port.postMessage).toHaveBeenCalledWith(expect.objectContaining({ requestId: 'model-5', error: expect.stringContaining('过多') }));
  });

  it('supports a sustained HTTP measurement without consuming the model request budget', async () => {
    const { execute, initialize, dispatch } = setup(['http.request', 'model.generate']);
    initialize();
    for (let index = 1; index <= 4100; index++) {
      dispatch(request(`http-${index}`, 'http.request', { url: 'https://example.test' }));
      await Promise.resolve();
    }
    dispatch(request('model-1', 'model.generate', { prompt: '分析测量结果' }));
    expect(execute).toHaveBeenCalledTimes(4101);
  });

  it('drops late results after teardown', async () => {
    const { port, execute, initialize, dispatch } = setup();
    initialize();
    port.postMessage.mockClear();
    let finish!: (value: unknown) => void;
    execute.mockImplementation(() => new Promise(resolve => { finish = resolve; }));
    dispatch(request());
    cleanups.splice(0).forEach(cleanup => cleanup());
    finish('late');
    await Promise.resolve();
    expect(port.postMessage).not.toHaveBeenCalled();
  });
});

describe('isolated application document', () => {
  it('keeps malicious closing tags and scripts out of the trusted bootstrap', () => {
    const content: AgentAppContent = { sdkVersion: 1, html: '<base href="https://evil.test"><meta http-equiv="refresh" content="0;url=https://evil.test">'
      + '<button onclick="steal()">Start</button><script>steal()</script><iframe src="https://evil.test"></iframe>',
    css: '</style><script>steal()</script>', source: '', permissions: [] };
    const html = buildApplicationDocument(content, 'const value = "</script><script>steal()</script>";', 'secret');
    const document = new DOMParser().parseFromString(html, 'text/html');
    expect(document.querySelectorAll('script')).toHaveLength(1);
    expect(document.querySelector('script')?.textContent).not.toContain('steal()');
    expect(document.head.firstElementChild?.getAttribute('http-equiv')).toBe('Content-Security-Policy');
    const policy = document.head.firstElementChild?.getAttribute('content');
    expect(policy).toContain("connect-src 'none'");
    expect(policy).toContain("frame-src 'none'");
    expect(policy).toContain("form-action 'none'");
    expect(policy).not.toContain('navigate-to');
    expect(policy).not.toContain("script-src 'unsafe-inline'");
    expect(document.querySelector('base, iframe')).toBeNull();
  });

  it('serializes a self-contained bootstrap and exposes only the scoped SDK', () => {
    const html = buildApplicationDocument({ sdkVersion: 1, html: '<h1>测试</h1>', css: 'h1 {color: red}', source: '', permissions: [] }, 'void 0;', 'secret');
    const runtimeDocument = new DOMParser().parseFromString(html, 'text/html');
    const script = runtimeDocument.querySelector('script')!;
    Object.defineProperty(runtimeDocument, 'currentScript', { value: script });
    const runtimeWindow = { addEventListener: vi.fn() } as unknown as Window;
    const parent = { postMessage: vi.fn() };
    const ports = { port1: fakePort(), port2: fakePort() };
    function MockMessageChannel() { return ports; }
    new Function('window', 'document', 'parent', 'TextDecoder', 'MessageChannel', script.textContent!)(
      runtimeWindow, runtimeDocument, parent, TextDecoder, MockMessageChannel);
    const sdk = (runtimeWindow as unknown as { app: { storage: { get: (key: string) => Promise<unknown> } } }).app;
    expect(Object.keys(sdk)).toEqual(['ai', 'agent', 'http', 'storage']);
    expect(Object.isFrozen(sdk)).toBe(true);
    expect(runtimeDocument.body.textContent).toContain('测试');
    expect(parent.postMessage).toHaveBeenCalledWith(expect.objectContaining({ type: 'ready', channel: 'secret' }), '*', [ports.port2]);
    expect(runtimeDocument.querySelectorAll('script')).toHaveLength(1);
    ports.port1.onmessage?.(new MessageEvent('message', { data: { protocol: 'x-agent-app', sdkVersion: 1, channel: 'secret', type: 'initialize' } }));
    expect(runtimeDocument.querySelectorAll('script')).toHaveLength(2);
    expect(runtimeDocument.querySelectorAll('script')[1]?.nonce).toBe(script.nonce);
  });

  it('cancels an HTTP operation without sending the AbortSignal across the message channel', async () => {
    const { sdk, ports } = bootSdk();
    const controller = new AbortController();
    const removeListener = vi.spyOn(controller.signal, 'removeEventListener');
    const result = sdk.http.request({ url: 'https://example.test', method: 'GET' }, { signal: controller.signal });
    const rejected = expect(result).rejects.toMatchObject({ name: 'AbortError' });
    expect(ports.port1.postMessage).toHaveBeenCalledWith(expect.objectContaining({ type: 'request',
      operation: 'http.request', payload: { url: 'https://example.test', method: 'GET' } }));
    controller.abort();
    await rejected;
    expect(ports.port1.postMessage).toHaveBeenCalledWith(expect.objectContaining({ type: 'cancel', requestId: '1' }));
    expect(removeListener).toHaveBeenCalledWith('abort', expect.any(Function));
  });

  it('rejects already aborted HTTP operations and removes listeners after a successful response', async () => {
    const { sdk, ports } = bootSdk();
    const aborted = new AbortController();
    aborted.abort();
    await expect(sdk.http.request({ url: 'https://example.test' }, { signal: aborted.signal })).rejects.toMatchObject({ name: 'AbortError' });
    expect(ports.port1.postMessage).not.toHaveBeenCalled();
    const controller = new AbortController();
    const removeListener = vi.spyOn(controller.signal, 'removeEventListener');
    const result = sdk.http.request({ url: 'https://example.test' }, { signal: controller.signal });
    ports.port1.onmessage?.(new MessageEvent('message', { data: {
      protocol: 'x-agent-app', sdkVersion: 1, channel: 'secret', type: 'response', requestId: '1', value: { statusCode: 200 },
    } }));
    await expect(result).resolves.toEqual({ statusCode: 200 });
    expect(removeListener).toHaveBeenCalledWith('abort', expect.any(Function));
    ports.port1.postMessage.mockClear();
    controller.abort();
    expect(ports.port1.postMessage).not.toHaveBeenCalled();
  });
});

/** 将实际序列化的 bootstrap 放进最小文档，校验隔离页面的原生 SDK 生命周期。 */
function bootSdk() {
  const html = buildApplicationDocument({ sdkVersion: 1, html: '', css: '', source: '', permissions: [] }, '', 'secret');
  const runtimeDocument = new DOMParser().parseFromString(html, 'text/html');
  const script = runtimeDocument.querySelector('script')!;
  Object.defineProperty(runtimeDocument, 'currentScript', { value: script });
  const runtimeWindow = { addEventListener: vi.fn() };
  const parent = { postMessage: vi.fn() };
  const ports = { port1: fakePort(), port2: fakePort() };
  function MockMessageChannel() { return ports; }
  new Function('window', 'document', 'parent', 'TextDecoder', 'MessageChannel', script.textContent!)(
    runtimeWindow, runtimeDocument, parent, TextDecoder, MockMessageChannel);
  const sdk = (runtimeWindow as unknown as { app: {
    http: { request: (data: unknown, options?: { signal: AbortSignal }) => Promise<unknown> };
  } }).app;
  return { sdk, ports };
}
