// @vitest-environment jsdom
import { createApp, defineComponent, h, nextTick } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { deleteAgentApp, listAgentApps } from '@/api/agent-app';
import AppsView from './AppsView.vue';

vi.mock('@/api/agent-app', () => ({ listAgentApps: vi.fn(), deleteAgentApp: vi.fn() }));
const { confirmDialog } = vi.hoisted(() => ({ confirmDialog: vi.fn<() => Promise<void>>() }));
vi.mock('element-plus', () => ({ ElMessageBox: { confirm: confirmDialog } }));
const cleanups: (() => void)[] = [];
afterEach(() => cleanups.splice(0).forEach(cleanup => cleanup()));
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(listAgentApps).mockResolvedValue([
    { id: 'new-app', name: '会议待办', description: '整理会议', currentVersion: 'v1' },
  ]);
});

async function mount() {
  const container = document.createElement('div');
  document.body.append(container);
  const app = createApp(AppsView);
  app.component('RouterLink', defineComponent({ props: ['to'], setup: (props, { slots }) => () => h('a', { href: props.to }, slots.default?.()) }));
  app.component('el-icon', defineComponent({ setup: (_, { slots }) => () => h('span', slots.default?.()) }));
  app.mount(container);
  cleanups.push(() => { app.unmount(); container.remove(); });
  await vi.waitFor(() => expect(listAgentApps).toHaveBeenCalledOnce());
  await nextTick();
  return container;
}

describe('application directory', () => {
  it('opens persisted applications through the shared application route', async () => {
    const container = await mount();
    expect(container.querySelector('a[href="/apps/new-app"]')?.textContent).toContain('会议待办');
    expect(container.querySelector('a[href="/apps/new-app?panel=edit"]')?.textContent).toContain('编辑');
    expect(container.querySelector('a[href="/apps/new-app?panel=versions"]')?.textContent).toContain('版本记录');
  });

  it('waits for confirmation before deleting the selected application and removing its card', async () => {
    let confirm!: () => void;
    confirmDialog.mockReturnValue(new Promise<void>(resolve => { confirm = resolve; }));
    vi.mocked(deleteAgentApp).mockResolvedValue(true);
    const container = await mount();
    container.querySelector<HTMLButtonElement>('[aria-label="删除会议待办"]')!.click();
    expect(deleteAgentApp).not.toHaveBeenCalled();
    confirm();
    await vi.waitFor(() => expect(deleteAgentApp).toHaveBeenCalledWith('new-app'));
    await vi.waitFor(() => expect(container.querySelector('.app-card')).toBeNull());
    expect(container.textContent).toContain('暂无智能应用');
  });

  it('retains the application when deletion is cancelled', async () => {
    confirmDialog.mockRejectedValue('cancel');
    const container = await mount();
    container.querySelector<HTMLButtonElement>('[aria-label="删除会议待办"]')!.click();
    await nextTick();
    expect(deleteAgentApp).not.toHaveBeenCalled();
    expect(container.querySelector('.app-card')).not.toBeNull();
    expect(container.querySelector('[role="alert"]')).toBeNull();
  });

  it('keeps the card and shows the error when deletion fails', async () => {
    confirmDialog.mockResolvedValue(undefined);
    vi.mocked(deleteAgentApp).mockRejectedValue(new Error('对象存储暂不可用'));
    const container = await mount();
    container.querySelector<HTMLButtonElement>('[aria-label="删除会议待办"]')!.click();
    await vi.waitFor(() => expect(container.querySelector('[role="alert"]')?.textContent).toContain('对象存储暂不可用'));
    expect(container.querySelector('.app-card')).not.toBeNull();
  });
});
