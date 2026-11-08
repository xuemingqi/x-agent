// @vitest-environment jsdom
import { createPinia } from 'pinia';
import { createApp, defineComponent, h, nextTick } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { analyzeHttpRequest, getHttpLoadTest, sendHttpRequest, startHttpLoadTest, stopHttpLoadTest } from '@/api/http-lab';
import { useModelSettingStore } from '@/stores/model-setting';
import type { HttpExecution, HttpLoadResult } from '@/types/http-lab';
import HttpLabView from './HttpLabView.vue';

vi.mock('@/api/http-lab', () => ({ analyzeHttpRequest: vi.fn(), getHttpLoadTest: vi.fn(), sendHttpRequest: vi.fn(), startHttpLoadTest: vi.fn(), stopHttpLoadTest: vi.fn() }));
const cleanups: (() => void)[] = [];
beforeEach(() => vi.clearAllMocks());
afterEach(() => { cleanups.splice(0).forEach(cleanup => cleanup()); vi.useRealTimers(); });

async function settle(): Promise<void> {
  for (let index = 0; index < 8; index++) await nextTick();
}

async function mountView() {
  const container = document.createElement('div');
  document.body.append(container);
  const pinia = createPinia();
  const app = createApp(HttpLabView).use(pinia);
  app.component('RouterLink', defineComponent({ setup: (_, { slots }) => () => h('a', slots.default?.()) }));
  const settings = useModelSettingStore(pinia);
  settings.loaded = true;
  settings.models = [{ id: '42', name: 'My Model', modelName: 'model', modelUrl: 'https://example.com/v1', contextWindowSize: 32768, temperature: null, isDefault: true }];
  app.mount(container);
  await settle();
  const unmount = () => { app.unmount(); container.remove(); };
  cleanups.push(unmount);
  return { container, unmount };
}

function click(container: HTMLElement, selector: string): void {
  container.querySelector<HTMLButtonElement>(selector)!.click();
}

function setValue(container: HTMLElement, label: string, value: string): void {
  const input = container.querySelector<HTMLInputElement>(`[aria-label="${label}"]`)!;
  input.value = value;
  input.dispatchEvent(new Event('input', { bubbles: true }));
}

const response: HttpExecution = { url: 'https://postman-echo.com/get', statusCode: 200, elapsedMs: 42, responseBytes: 11, body: '{"ok":true}', headers: {}, error: '', truncated: false };
const running: HttpLoadResult = { id: 'run-1', status: 'RUNNING', method: 'GET', url: 'https://postman-echo.com/get', concurrency: 5, durationSeconds: 30, requestsPerSecond: 10, elapsedSeconds: 1, started: 10, completed: 8, failed: 0, cancelled: 0, throughput: 8, errorRate: 0, averageMs: 42, p50Ms: 40, p95Ms: 50, p99Ms: 50, statusCounts: { '200': 8 }, timeline: [], message: '' };

describe('HTTP lab workflows', () => {
  it('keeps an in-flight response with its original request when another draft is selected', async () => {
    let resolve!: (result: HttpExecution) => void;
    vi.mocked(sendHttpRequest).mockReturnValue(new Promise(done => { resolve = done; }));
    const { container } = await mountView();
    click(container, '[data-testid="send"]');
    await settle();
    click(container, '[data-testid="add-request"]');
    await settle();
    setValue(container, '请求地址', 'https://example.test/second');
    resolve(response);
    await settle();
    expect(container.querySelector('.response-code')).toBeNull();
    click(container, '.request-item');
    await settle();
    expect(container.querySelector('.response-code')?.textContent).toContain('"ok": true');
    expect(sendHttpRequest).toHaveBeenCalledTimes(1);
  });

  it('analyzes the executed snapshot after the current editor has changed', async () => {
    vi.mocked(sendHttpRequest).mockResolvedValue(response);
    vi.mocked(analyzeHttpRequest).mockResolvedValue({ summary: '请求成功', observations: ['HTTP 200'], suggestions: [], limitations: [] });
    const { container } = await mountView();
    click(container, '[data-testid="send"]');
    await settle();
    setValue(container, '请求地址', 'https://example.test/edited');
    await settle();
    click(container, '[data-testid="analyze"]');
    await settle();
    expect(analyzeHttpRequest).toHaveBeenCalledWith(expect.objectContaining({ request: expect.objectContaining({ url: response.url }), response }), expect.any(AbortSignal));
    expect(container.querySelector('.snapshot-note')?.textContent).toContain('上一次');
    expect(container.querySelector('.analysis-summary')?.textContent).toBe('请求成功');
  });

  it('polls an active load test and stops it when leaving the page', async () => {
    vi.useFakeTimers();
    vi.mocked(startHttpLoadTest).mockResolvedValue(running);
    vi.mocked(getHttpLoadTest).mockResolvedValue({ ...running, completed: 12 });
    vi.mocked(stopHttpLoadTest).mockResolvedValue({ ...running, status: 'STOPPED' });
    const { container, unmount } = await mountView();
    click(container, '[data-testid="load-tab"]');
    await settle();
    click(container, '[data-testid="start-load"]');
    await settle();
    expect(startHttpLoadTest).toHaveBeenCalledWith(expect.objectContaining({ concurrency: 5, durationSeconds: 30, requestsPerSecond: 10 }));
    await vi.advanceTimersByTimeAsync(1000);
    await settle();
    expect(getHttpLoadTest).toHaveBeenCalledWith('run-1');
    expect(container.querySelector('[data-testid="stop-load"]')).not.toBeNull();
    unmount();
    cleanups.pop();
    expect(stopHttpLoadTest).toHaveBeenCalledWith('run-1');
    await vi.advanceTimersByTimeAsync(5000);
    expect(getHttpLoadTest).toHaveBeenCalledTimes(1);
  });

  it('stops a run accepted after the page was unmounted', async () => {
    let resolve!: (result: HttpLoadResult) => void;
    vi.mocked(startHttpLoadTest).mockReturnValue(new Promise(done => { resolve = done; }));
    vi.mocked(stopHttpLoadTest).mockResolvedValue({ ...running, status: 'STOPPED' });
    const { container, unmount } = await mountView();
    click(container, '[data-testid="load-tab"]');
    await settle();
    click(container, '[data-testid="start-load"]');
    await settle();
    unmount();
    cleanups.pop();
    resolve(running);
    await settle();
    expect(stopHttpLoadTest).toHaveBeenCalledWith('run-1');
  });
});
