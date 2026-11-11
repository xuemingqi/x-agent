// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createApp, defineComponent, h, nextTick } from 'vue';
import { ElMessage } from 'element-plus';

import { createUserMcpServer, listMcpServers, setMcpServerEnabled } from '@/api/mcp';
import { MCP_TRANSPORT_TYPE } from '@/types/mcp';
import type { McpServer } from '@/types/mcp';
import McpManagementPanel from './McpManagementPanel.vue';

vi.mock('@/api/mcp', () => ({
  createUserMcpServer: vi.fn(), listMcpServers: vi.fn(), setMcpServerEnabled: vi.fn(),
}));
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn() }, ElMessageBox: { confirm: vi.fn() },
}));

const cleanups: (() => void)[] = [];
const server = (): McpServer => ({
  id: '42', name: 'remote-search', description: 'Test server',
  transportType: MCP_TRANSPORT_TYPE.STREAMABLE_HTTP, endpoint: 'https://example.com/mcp', headers: {}, queryParameters: {},
  enabled: true, runtimeSupported: true, requestTimeoutSeconds: 60, initializationTimeoutSeconds: 30,
});

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(listMcpServers).mockResolvedValue([server()]);
  vi.mocked(setMcpServerEnabled).mockImplementation(async (_, enabled) => enabled);
  vi.mocked(createUserMcpServer).mockResolvedValue(server());
});
afterEach(() => cleanups.splice(0).forEach((cleanup) => cleanup()));

async function settle() {
  for (let i = 0; i < 8; i++) await nextTick();
}

async function mountPanel(openDialog = false) {
  const container = document.createElement('div');
  document.body.append(container);
  const app = createApp(McpManagementPanel);
  const wrapper = defineComponent({ setup: (_, { slots }) => () => h('div', slots.default?.()) });
  for (const name of ['el-icon', 'el-tag', 'el-empty', 'el-form-item', 'el-input-number', 'el-form']) {
    app.component(name, wrapper);
  }
  app.component('el-dialog', defineComponent({
    props: ['modelValue'],
    setup: (props, { slots }) => () => props.modelValue
      ? h('div', [slots.header?.(), slots.default?.(), slots.footer?.()]) : null,
  }));
  app.component('el-input', defineComponent({
    props: ['modelValue', 'disabled'], emits: ['update:modelValue'],
    setup: (props, { emit }) => () => h('input', {
      value: props.modelValue, disabled: props.disabled,
      onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLInputElement).value),
    }),
  }));
  app.component('el-button', defineComponent({ setup: (_, { slots }) => () => h('button', slots.default?.()) }));
  app.component('el-switch', defineComponent({
    props: ['modelValue', 'loading', 'disabled'], emits: ['change'],
    setup: (props, { emit }) => () => h('button', {
      role: 'switch', 'aria-checked': String(props.modelValue), disabled: props.loading || props.disabled,
      onClick: () => emit('change', !props.modelValue),
    }),
  }));
  app.directive('loading', {});
  const instance = app.mount(container);
  await settle();
  if (openDialog) {
    (instance as unknown as { openCreate: () => void }).openCreate();
    await settle();
  }
  cleanups.push(() => { app.unmount(); container.remove(); });
  return container;
}

describe('MCP enablement', () => {
  it('toggles its own connection using one persisted enabled state', async () => {
    const container = await mountPanel();
    expect(container.querySelector('.scope-switch')).toBeNull();
    const toggle = container.querySelector<HTMLButtonElement>('[role="switch"]')!;
    toggle.click();
    await settle();
    expect(setMcpServerEnabled).toHaveBeenCalledWith('42', false);
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

  it('can re-enable a stopped connection directly', async () => {
    vi.mocked(listMcpServers).mockResolvedValue([{ ...server(), enabled: false }]);
    const container = await mountPanel();
    const toggle = container.querySelector<HTMLButtonElement>('[role="switch"]')!;
    expect(toggle.disabled).toBe(false);
    expect(toggle.getAttribute('aria-checked')).toBe('false');
    toggle.click();
    await settle();
    expect(setMcpServerEnabled).toHaveBeenCalledWith('42', true);
    expect(toggle.getAttribute('aria-checked')).toBe('true');
  });
});


describe('HTTP MCP configuration', () => {
  function fillInput(container: HTMLElement, placeholder: string, value: string): void {
    const input = container.querySelector<HTMLInputElement>(`input[placeholder="${placeholder}"]`)!;
    input.value = value;
    input.dispatchEvent(new Event('input', { bubbles: true }));
  }

  it('creates a Streamable HTTP connection with headers and query parameters', async () => {
    const container = await mountPanel(true);
    fillInput(container, '例如：internal-search', 'remote-search');
    fillInput(container, '告诉 Agent 什么时候应该使用这个 MCP', '搜索远程文档');
    fillInput(container, 'https://mcp.example.com/mcp', 'https://example.com/mcp');
    const sections = container.querySelectorAll('.mcp-key-value-section');
    sections[0]!.querySelector<HTMLButtonElement>('button')!.click();
    sections[1]!.querySelector<HTMLButtonElement>('button')!.click();
    await settle();
    fillInput(container, 'Header 名称', 'Authorization');
    fillInput(container, 'Header 值', 'Bearer test-token');
    fillInput(container, '参数名称', 'region');
    fillInput(container, '参数值', 'cn');
    container.querySelector<HTMLButtonElement>('.mcp-dialog-footer button:last-child')!.click();
    await settle();

    expect(createUserMcpServer).toHaveBeenCalledWith({
      name: 'remote-search', description: '搜索远程文档',
      transportType: MCP_TRANSPORT_TYPE.STREAMABLE_HTTP, endpoint: 'https://example.com/mcp',
      headers: { Authorization: 'Bearer test-token' }, queryParameters: { region: 'cn' },
      enabled: true, requestTimeoutSeconds: 60, initializationTimeoutSeconds: 30,
    });
    expect(container.textContent).not.toContain('启动命令');
    expect(container.textContent).not.toContain('环境变量');
  });

  it('requires a remote service address before saving', async () => {
    const container = await mountPanel(true);
    fillInput(container, '例如：internal-search', 'remote-search');
    fillInput(container, '告诉 Agent 什么时候应该使用这个 MCP', '搜索远程文档');
    container.querySelector<HTMLButtonElement>('.mcp-dialog-footer button:last-child')!.click();
    await settle();

    expect(createUserMcpServer).not.toHaveBeenCalled();
    expect(ElMessage.error).toHaveBeenCalledWith('请填写 Streamable HTTP 服务地址');
  });
});
