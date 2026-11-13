// @vitest-environment jsdom
import { createApp, h, nextTick, ref } from 'vue';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { compileApplicationSource } from '@/utils/app-compiler';
import { createApplicationBridge } from '@/utils/app-runtime';
import type { AgentAppContent } from '@/types/agent-app';
import ApplicationRuntime from './ApplicationRuntime.vue';

vi.mock('@/utils/app-compiler', () => ({ compileApplicationSource: vi.fn() }));
vi.mock('@/utils/app-runtime', () => ({
  buildApplicationDocument: (_: unknown, javascript: string) => `<body>${javascript}</body>`,
  createApplicationToken: () => 'channel', createApplicationBridge: vi.fn(),
}));
const cleanups: (() => void)[] = [];
afterEach(() => { cleanups.splice(0).forEach(cleanup => cleanup()); vi.resetAllMocks(); });

const content: AgentAppContent = { sdkVersion: 1, html: '', css: '', source: 'void 0;', permissions: [] };
function mount() {
  const version = ref('v1');
  const container = document.createElement('div');
  const app = createApp({ setup: () => () => h(ApplicationRuntime, { applicationId: 'app-1', version: version.value, content }) });
  app.mount(container);
  const unmount = () => app.unmount();
  cleanups.push(unmount);
  return { container, version, unmount };
}

describe('application runtime lifecycle', () => {
  it('runs without same-origin privileges and closes its capability channel on unmount', async () => {
    const dispose = vi.fn();
    vi.mocked(compileApplicationSource).mockResolvedValue('compiled');
    vi.mocked(createApplicationBridge).mockReturnValue(dispose);
    const { container, unmount } = mount();
    await vi.waitFor(() => expect(container.querySelector('iframe')).not.toBeNull());
    expect(container.querySelector('iframe')?.getAttribute('sandbox')).toBe('allow-scripts allow-forms');
    expect(container.querySelector('iframe')?.getAttribute('allow')).toContain("microphone 'none'");
    unmount();
    cleanups.length = 0;
    expect(dispose).toHaveBeenCalled();
  });

  it('discards a compiler result belonging to an old version', async () => {
    let finish!: (value: string) => void;
    vi.mocked(compileApplicationSource).mockReturnValueOnce(new Promise(resolve => { finish = resolve; })).mockResolvedValueOnce('new');
    vi.mocked(createApplicationBridge).mockReturnValue(vi.fn());
    const { container, version } = mount();
    version.value = 'v2';
    await nextTick();
    await vi.waitFor(() => expect(container.querySelector('iframe')?.srcdoc).toContain('new'));
    finish('old');
    await nextTick();
    await nextTick();
    expect(container.querySelector('iframe')?.srcdoc).not.toContain('old');
    expect(createApplicationBridge).toHaveBeenCalledOnce();
  });

  it('shows syntax errors without starting an application channel', async () => {
    vi.mocked(compileApplicationSource).mockRejectedValue(new Error('第 1 行：语法错误'));
    const { container } = mount();
    await vi.waitFor(() => expect(container.textContent).toContain('语法错误'));
    expect(container.querySelector('iframe')).toBeNull();
    expect(createApplicationBridge).not.toHaveBeenCalled();
  });
});
