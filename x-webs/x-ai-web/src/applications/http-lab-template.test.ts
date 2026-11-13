// @vitest-environment jsdom
import { readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';

import { compileApplicationSource } from '@/utils/app-compiler';
import type { AgentAppContent } from '@/types/agent-app';

const resource = resolve(process.cwd(), '../../x-services/x-ai/src/main/resources/agent-app-templates/http-lab');
const template: AgentAppContent = {
  sdkVersion: 1, html: readFileSync(join(resource, 'index.html'), 'utf8'),
  css: readFileSync(join(resource, 'style.css'), 'utf8'), source: readFileSync(join(resource, 'app.ts'), 'utf8'),
  permissions: ['http.request', 'model.generate', 'storage'],
};
let javascript = '';

beforeAll(async () => { javascript = await compileApplicationSource(template); });
afterEach(() => {
  window.dispatchEvent(new Event('pagehide'));
  document.body.replaceChildren();
  vi.useRealTimers();
});

describe('HTTP laboratory TypeScript application', () => {
  it('runs through the generic SDK and keeps the measured response paired with the sent request snapshot', async () => {
    const sdk = await start();
    input('url').value = 'https://example.test/first?existing=a%2Fb';
    select('method').value = 'POST';
    input('body').value = '{"message":"你好"}';
    button('add-query').click();
    document.querySelector<HTMLInputElement>('#query-fields [data-field="name"]')!.value = 'q';
    document.querySelector<HTMLInputElement>('#query-fields [data-field="value"]')!.value = 'a&b 中文';
    button('send').click();
    await settle();

    expect(sdk.http.request).toHaveBeenCalledWith(expect.objectContaining({
      method: 'POST', url: 'https://example.test/first?existing=a%2Fb', body: '{"message":"你好"}',
      query: [{ name: 'q', value: 'a&b 中文', enabled: true }],
    }), { signal: expect.any(AbortSignal) });
    expect(document.querySelector('#response-status')?.textContent).toContain('200 · 12.0 ms');
    expect(document.querySelector('#response-body')?.textContent).toContain('response');

    input('url').value = 'https://example.test/edited';
    select('analysis-target').value = 'response';
    button('analyze').click();
    await settle();
    const prompt = JSON.parse(sdk.ai.generate.mock.calls[0]![0].prompt);
    expect(prompt.request.url).toBe('https://example.test/first?existing=a%2Fb');
    expect(prompt.response.statusCode).toBe(200);
    expect(document.querySelector('#analysis')?.textContent).toContain('基于实际快照');
  });

  it('saves only request drafts and restores them without persisting response bodies', async () => {
    const sdk = await start();
    input('url').value = 'https://example.test/saved';
    input('request-name').value = '保存的请求';
    button('send').click();
    await settle();
    button('save-drafts').click();
    await settle();
    const saved = sdk.storage.set.mock.calls[0]![1];
    expect(saved[0]).toMatchObject({ name: '保存的请求', request: { url: 'https://example.test/saved' } });
    expect(saved[0]).not.toHaveProperty('response');
    window.dispatchEvent(new Event('pagehide'));
    await start(saved);
    expect(input('url').value).toBe('https://example.test/saved');
    expect(input('request-name').value).toBe('保存的请求');
    expect(document.querySelector('#response-status')?.textContent).toBe('尚未发送请求');
  });

  it('paces requests in TypeScript, computes exact server-side percentiles and counts non-2xx as failures', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date', 'performance'] });
    const sdk = await start();
    const values = [10, 20, 30, 40, 100];
    sdk.http.request.mockImplementation(async () => response(values.shift() ?? 100, values.length === 2 ? 500 : 200));
    input('url').value = 'https://example.test/measurement';
    input('concurrency').value = '2'; input('rate').value = '5'; input('duration').value = '1';
    button('start-load').click();
    await vi.advanceTimersByTimeAsync(1100);

    expect(sdk.http.request).toHaveBeenCalledTimes(5);
    expect(document.querySelector('#load-state')?.textContent).toBe('已完成');
    expect(metric('完成 / 次')).toBe('5');
    expect(metric('失败 / 次')).toBe('1');
    expect(metric('平均 / ms')).toBe('40.0');
    expect(metric('P50 / ms')).toBe('30.0');
    expect(metric('P95 / ms')).toBe('100.0');
    expect(document.querySelector('#load-summary')?.textContent).toContain('延迟样本 5 次');
    select('analysis-target').value = 'load'; button('analyze').click(); await settle();
    const snapshot = JSON.parse(sdk.ai.generate.mock.calls[0]![0].prompt).loadTest;
    expect(snapshot).toMatchObject({ completed: 5, failed: 1, latencySamples: 5, p50Ms: 30, p99Ms: 100 });
    expect(snapshot.request).toBeUndefined();
  });

  it('keeps the concurrency bound, skips saturated slots and cancels in-flight SDK requests on stop', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date', 'performance'] });
    const sdk = await start();
    let active = 0;
    let peak = 0;
    const signals: AbortSignal[] = [];
    sdk.http.request.mockImplementation((_request, options) => new Promise((resolve, reject) => {
      active++; peak = Math.max(active, peak); signals.push(options.signal);
      options.signal.addEventListener('abort', () => { active--; reject(new DOMException('aborted', 'AbortError')); });
    }));
    input('url').value = 'https://example.test/slow';
    input('concurrency').value = '2'; input('rate').value = '10'; input('duration').value = '10';
    button('start-load').click();
    await vi.advanceTimersByTimeAsync(550);
    expect(peak).toBe(2);
    expect(sdk.http.request).toHaveBeenCalledTimes(2);
    button('stop-load').click(); await settle();

    expect(signals.every(signal => signal.aborted)).toBe(true);
    expect(document.querySelector('#load-state')?.textContent).toBe('已停止');
    expect(document.querySelector('#load-summary')?.textContent).toContain('取消 2 次');
    expect(document.querySelector('#load-summary')?.textContent).toContain('延迟样本 0 次');
    expect(metric('P50 / ms')).toBe('—');
    await vi.advanceTimersByTimeAsync(1000);
    expect(sdk.http.request).toHaveBeenCalledTimes(2);
  });

  it('rejects invalid requests and measurement bounds before calling any network capability', async () => {
    const sdk = await start();
    input('url').value = 'file:///etc/hosts'; button('send').click(); await settle();
    expect(document.querySelector('#error')?.textContent).toContain('HTTP / HTTPS');
    input('url').value = 'https://example.test'; input('concurrency').value = '21';
    button('start-load').click(); await settle();
    expect(document.querySelector('#error')?.textContent).toContain('并发范围');
    expect(sdk.http.request).not.toHaveBeenCalled();
  });

  it('does not mix SDK failures with the server-side latency measurements', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date', 'performance'] });
    const sdk = await start();
    sdk.http.request.mockRejectedValueOnce(new Error('SDK unavailable'));
    input('url').value = 'https://example.test'; input('duration').value = '1'; input('rate').value = '2';
    button('start-load').click(); await vi.advanceTimersByTimeAsync(1100);
    expect(metric('完成 / 次')).toBe('2'); expect(metric('失败 / 次')).toBe('1');
    expect(metric('平均 / ms')).toBe('12.0');
    expect(document.querySelector('#load-summary')?.textContent).toContain('延迟样本 1 次');
  });
});

async function start(stored: unknown = null) {
  document.body.innerHTML = template.html;
  const sdk = {
    http: { request: vi.fn<(request: Record<string, unknown>, options: { signal: AbortSignal }) => Promise<ReturnType<typeof response>>>()
      .mockResolvedValue(response()) },
    ai: { generate: vi.fn<(request: { prompt: string; systemPrompt: string }) => Promise<{ text: string }>>()
      .mockResolvedValue({ text: JSON.stringify({ summary: '基于实际快照', observations: ['真实结果'], suggestions: ['继续验证'], limitations: ['单机测量'] }) }) },
    storage: { get: vi.fn().mockResolvedValue(stored), set: vi.fn().mockResolvedValue(true) },
  };
  await new Function('app', 'source', 'return eval(source)')(sdk, javascript);
  return sdk;
}

function response(elapsedMs = 12, statusCode = 200) {
  return { url: 'https://example.test', statusCode, elapsedMs, responseBytes: 8, truncated: false,
    body: 'response', headers: { 'content-type': ['text/plain'] }, error: '' };
}

function input(id: string): HTMLInputElement { return document.getElementById(id) as HTMLInputElement; }
function button(id: string): HTMLButtonElement { return document.getElementById(id) as HTMLButtonElement; }
function select(id: string): HTMLSelectElement { return document.getElementById(id) as HTMLSelectElement; }
function metric(label: string): string | null | undefined {
  return [...document.querySelectorAll('.metric')].find(item => item.querySelector('span')?.textContent === label)
    ?.querySelector('strong')?.textContent;
}
async function settle(): Promise<void> { for (let index = 0; index < 12; index++) await Promise.resolve(); }
