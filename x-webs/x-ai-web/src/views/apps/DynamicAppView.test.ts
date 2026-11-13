// @vitest-environment jsdom
import { createApp, defineComponent, h, nextTick, reactive } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { activateAgentAppVersion, getAgentApp, listAgentAppVersions, saveAgentApp } from '@/api/agent-app';
import { compileApplicationSource } from '@/utils/app-compiler';
import type { AgentAppDetail } from '@/types/agent-app';
import DynamicAppView from './DynamicAppView.vue';

const state = vi.hoisted(() => ({ route: { params: { appId: 'app-1' }, query: {} as Record<string, string> },
  push: vi.fn(), replace: vi.fn(), runtimeMounted: vi.fn() }));
vi.mock('vue-router', () => ({ useRoute: () => state.route, useRouter: () => ({ push: state.push, replace: state.replace }) }));
vi.mock('@/api/agent-app', () => ({ getAgentApp: vi.fn(), listAgentAppVersions: vi.fn(),
  saveAgentApp: vi.fn(), activateAgentAppVersion: vi.fn() }));
vi.mock('@/utils/app-compiler', () => ({ compileApplicationSource: vi.fn().mockResolvedValue('void 0;') }));
vi.mock('@/components/apps/ApplicationRuntime.vue', () => ({ default: {
  props: ['version'],
  setup: (props: { version: string }) => { state.runtimeMounted(props.version); return () => null; },
} }));

const cleanups: (() => void)[] = [];
afterEach(() => cleanups.splice(0).forEach(cleanup => cleanup()));
beforeEach(() => {
  vi.clearAllMocks();
  state.route = reactive({ params: { appId: 'app-1' }, query: {} });
  state.replace.mockImplementation(async location => { state.route.query = location.query; });
});
const detail = (version = 'v1', name = '会议待办'): AgentAppDetail => ({ id: 'app-1', name,
  description: '整理会议', currentVersion: version,
  content: { sdkVersion: 1, html: '<h1>会议待办</h1>', css: '', source: 'void 0;', permissions: ['storage'] } });

function button(container: HTMLElement, text: string): HTMLButtonElement {
  return [...container.querySelectorAll('button')].find(element => element.textContent?.trim() === text)!;
}

async function mount(panel: 'run' | 'edit' | 'versions' = 'edit') {
  if (panel !== 'run') state.route.query.panel = panel;
  vi.mocked(getAgentApp).mockResolvedValue(detail());
  const container = document.createElement('div');
  document.body.append(container);
  const app = createApp(DynamicAppView);
  app.component('RouterLink', defineComponent({ setup: (_, { slots }) => () => h('a', slots.default?.()) }));
  app.component('el-icon', defineComponent({ setup: (_, { slots }) => () => h('span', slots.default?.()) }));
  app.mount(container);
  cleanups.push(() => { app.unmount(); container.remove(); });
  await vi.waitFor(() => expect(container.querySelector('h1')?.textContent).toBe('会议待办'));
  return container;
}

describe('dynamic application management', () => {
  it('saves an edited source as a new version using the version originally opened', async () => {
    vi.mocked(saveAgentApp).mockResolvedValue(detail('v2', '新版会议待办'));
    const container = await mount();
    expect(state.runtimeMounted).not.toHaveBeenCalled();
    const source = container.querySelector('textarea')!;
    source.value = 'const count: number = 1;';
    source.dispatchEvent(new Event('input', { bubbles: true }));
    await nextTick();
    button(container, '保存并运行').click();
    await vi.waitFor(() => expect(saveAgentApp).toHaveBeenCalledWith(expect.objectContaining({
      baseVersion: 'v1', source: 'const count: number = 1;', permissions: ['storage'],
    }), 'app-1'));
    await vi.waitFor(() => expect(container.querySelector('h1')?.textContent).toBe('新版会议待办'));
    await vi.waitFor(() => expect(container.querySelector('textarea')).toBeNull());
    expect(state.route.query).not.toHaveProperty('panel');
  });

  it('keeps the newly opened application when an earlier save finishes after navigation', async () => {
    let finish!: (value: AgentAppDetail) => void;
    vi.mocked(saveAgentApp).mockReturnValue(new Promise(resolve => { finish = resolve; }));
    const container = await mount();
    button(container, '保存并运行').click();
    await vi.waitFor(() => expect(saveAgentApp).toHaveBeenCalledOnce());
    vi.mocked(getAgentApp).mockResolvedValue({ ...detail('other-v1', '另一个应用'), id: 'app-2' });
    state.route.params.appId = 'app-2';
    await vi.waitFor(() => expect(container.querySelector('h1')?.textContent).toBe('另一个应用'));
    finish(detail('v2', '旧应用刚保存的新版本'));
    await nextTick();
    await nextTick();
    expect(container.querySelector('h1')?.textContent).toBe('另一个应用');
    expect(container.querySelector('input')?.disabled).toBe(false);
  });

  it('runs the newly published version when a pending save finishes after returning to the same application', async () => {
    let finish!: (value: AgentAppDetail) => void;
    vi.mocked(saveAgentApp).mockReturnValue(new Promise(resolve => { finish = resolve; }));
    const container = await mount();
    button(container, '保存并运行').click();
    await vi.waitFor(() => expect(saveAgentApp).toHaveBeenCalledOnce());
    state.route.query = {};
    await vi.waitFor(() => expect(state.runtimeMounted).toHaveBeenLastCalledWith('v1'));

    finish(detail('v2', '新版会议待办'));

    await vi.waitFor(() => expect(state.runtimeMounted).toHaveBeenLastCalledWith('v2'));
    expect(container.querySelector('h1')?.textContent).toBe('新版会议待办');
    expect(state.route.query).not.toHaveProperty('panel');
    expect(state.replace).not.toHaveBeenCalled();
  });

  it('locks source edits while compiling and publishes the validated source snapshot', async () => {
    let finish!: (value: string) => void;
    vi.mocked(compileApplicationSource).mockReturnValueOnce(new Promise(resolve => { finish = resolve; }));
    vi.mocked(saveAgentApp).mockResolvedValue(detail('v2'));
    const container = await mount();
    button(container, '保存并运行').click();
    await nextTick();
    expect(container.querySelector('textarea')?.disabled).toBe(true);
    expect(container.querySelector('fieldset')?.disabled).toBe(true);
    expect(container.querySelector('input')?.disabled).toBe(true);
    finish('compiled');
    await vi.waitFor(() => expect(saveAgentApp).toHaveBeenCalledWith(expect.objectContaining({ source: 'void 0;' }), 'app-1'));
  });

  it('restores a recorded version without overwriting a concurrently changed publication', async () => {
    vi.mocked(listAgentAppVersions).mockResolvedValue([{ version: 'v0', name: '旧版会议待办', description: '',
      sizeBytes: 100, sha256: 'hash', createTime: '2026-11-12T12:00:00', current: false }]);
    vi.mocked(activateAgentAppVersion).mockRejectedValue(new Error('应用版本已更新，请重新读取后再修改'));
    const container = await mount('versions');
    await vi.waitFor(() => expect(container.textContent).toContain('旧版会议待办'));
    expect(state.runtimeMounted).not.toHaveBeenCalled();
    button(container, '恢复此版本').click();
    await vi.waitFor(() => expect(activateAgentAppVersion).toHaveBeenCalledWith('app-1', 'v0', 'v1'));
    await vi.waitFor(() => expect(container.querySelector('[role="alert"]')?.textContent).toContain('版本已更新'));
    expect(container.querySelector('h1')?.textContent).toBe('会议待办');
  });

  it('switches same-application management panels without reloading details or executing the application', async () => {
    vi.mocked(listAgentAppVersions).mockResolvedValue([]);
    const container = await mount('run');
    expect(state.runtimeMounted).toHaveBeenCalledOnce();
    expect(listAgentAppVersions).not.toHaveBeenCalled();

    state.route.query.panel = 'versions';
    await vi.waitFor(() => expect(container.querySelector('.application-versions')).not.toBeNull());
    expect(listAgentAppVersions).toHaveBeenCalledExactlyOnceWith('app-1');
    state.route.query.panel = 'edit';
    await vi.waitFor(() => expect(container.querySelector('textarea')).not.toBeNull());
    expect(getAgentApp).toHaveBeenCalledOnce();
    expect(state.runtimeMounted).toHaveBeenCalledOnce();
    button(container, '返回应用').click();
    await vi.waitFor(() => expect(state.runtimeMounted).toHaveBeenCalledTimes(2));
    expect(state.route.query).not.toHaveProperty('panel');
  });
});
