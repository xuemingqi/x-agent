// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createApp, defineComponent, h, nextTick } from 'vue';
import { ElMessage } from 'element-plus';

import { listMcpServers, listPublicMcpServers, setMcpServerEnabled } from '@/api/mcp';
import { MCP_SCOPE, MCP_TRANSPORT_TYPE } from '@/types/mcp';
import type { McpScope, McpServer } from '@/types/mcp';
import McpManagementPanel from './McpManagementPanel.vue';

vi.mock('@/api/mcp', () => ({
  listMcpServers: vi.fn(), listPublicMcpServers: vi.fn(), setMcpServerEnabled: vi.fn(),
}));
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() }, ElMessageBox: { confirm: vi.fn() },
}));

const cleanups: (() => void)[] = [];
const server = (scope: McpScope): McpServer => ({
  id: String(scope), name: `server-${scope}`, description: 'Test server', scope,
  transportType: MCP_TRANSPORT_TYPE.STREAMABLE_HTTP, arguments: [], headers: {}, queryParameters: {}, environment: {},
  enabled: true, userEnabled: true, editable: scope === MCP_SCOPE.USER, runtimeSupported: true,
  userCredentialConfigured: false, userCredentialRequired: false,
  requestTimeoutSeconds: 60, initializationTimeoutSeconds: 30,
});

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(listMcpServers).mockResolvedValue([server(MCP_SCOPE.USER), server(MCP_SCOPE.PUBLIC), server(MCP_SCOPE.INTERNAL_PUBLIC)]);
  vi.mocked(setMcpServerEnabled).mockImplementation(async (_, enabled) => enabled);
});
afterEach(() => cleanups.splice(0).forEach((cleanup) => cleanup()));

async function settle() {
  for (let i = 0; i < 8; i++) await nextTick();
}

async function mountPanel(administrator = false) {
  const container = document.createElement('div');
  document.body.append(container);
  const app = createApp(McpManagementPanel, { administrator });
  const wrapper = defineComponent({ setup: (_, { slots }) => () => h('div', slots.default?.()) });
  for (const name of ['el-icon', 'el-tag', 'el-empty', 'el-input', 'el-form-item', 'el-option', 'el-select', 'el-input-number', 'el-form']) {
    app.component(name, wrapper);
  }
  app.component('el-dialog', defineComponent({ setup: () => () => null }));
  app.component('el-button', defineComponent({ setup: (_, { slots }) => () => h('button', slots.default?.()) }));
  app.component('el-switch', defineComponent({
    props: ['modelValue', 'loading', 'disabled'], emits: ['change'],
    setup: (props, { emit }) => () => h('button', {
      role: 'switch', 'aria-checked': String(props.modelValue), disabled: props.loading || props.disabled,
      onClick: () => emit('change', !props.modelValue),
    }),
  }));
  app.directive('loading', {});
  app.mount(container);
  await settle();
  cleanups.push(() => { app.unmount(); container.remove(); });
  return container;
}

describe('MCP enablement', () => {
  it.each([MCP_SCOPE.USER, MCP_SCOPE.PUBLIC, MCP_SCOPE.INTERNAL_PUBLIC])('allows a personal toggle for scope %s', async (scope) => {
    const container = await mountPanel();
    if (scope !== MCP_SCOPE.USER) {
      container.querySelectorAll<HTMLButtonElement>('.scope-switch button')[1]!.click();
      await settle();
    }
    const card = Array.from(container.querySelectorAll('.mcp-card')).find((element) => element.textContent?.includes(`server-${scope}`))!;
    const toggle = card.querySelector<HTMLButtonElement>('[role="switch"]')!;
    toggle.click();
    await settle();
    expect(setMcpServerEnabled).toHaveBeenCalledWith(expect.objectContaining({ id: String(scope), scope, enabled: true }), false);
    expect(toggle.getAttribute('aria-checked')).toBe('false');
    toggle.click();
    await settle();
    expect(toggle.getAttribute('aria-checked')).toBe('true');
  });

  it('keeps the old state after failure and blocks repeated requests while saving', async () => {
    let reject!: (error: Error) => void;
    vi.mocked(setMcpServerEnabled).mockImplementation(() => new Promise((_, rejectRequest) => { reject = rejectRequest; }));
    const container = await mountPanel();
    const toggle = container.querySelector<HTMLButtonElement>('[role="switch"]')!;
    toggle.click();
    await settle();
    expect(toggle.disabled).toBe(true);
    toggle.click();
    expect(setMcpServerEnabled).toHaveBeenCalledTimes(1);
    reject(new Error('保存失败'));
    await settle();
    expect(toggle.getAttribute('aria-checked')).toBe('true');
    expect(toggle.disabled).toBe(false);
    expect(ElMessage.error).toHaveBeenCalledWith('保存失败');
  });

  it('does not let a personal switch override a stopped service', async () => {
    vi.mocked(listMcpServers).mockResolvedValue([{ ...server(MCP_SCOPE.USER), enabled: false }]);
    const container = await mountPanel();
    const toggle = container.querySelector<HTMLButtonElement>('[role="switch"]')!;
    expect(toggle.disabled).toBe(true);
    expect(toggle.getAttribute('aria-checked')).toBe('false');
    expect(container.textContent).toContain('服务已停用');
    toggle.click();
    expect(setMcpServerEnabled).not.toHaveBeenCalled();
  });

  it('shows the persisted personal choice from the administrator list', async () => {
    vi.mocked(listPublicMcpServers).mockResolvedValue([{ ...server(MCP_SCOPE.PUBLIC), userEnabled: false, editable: true }]);
    const container = await mountPanel(true);
    container.querySelectorAll<HTMLButtonElement>('.scope-switch button')[1]!.click();
    await settle();
    expect(container.querySelector('[role="switch"]')!.getAttribute('aria-checked')).toBe('false');
  });
});
