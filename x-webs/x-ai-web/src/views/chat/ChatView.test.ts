// @vitest-environment jsdom
import { createPinia } from 'pinia';
import { createApp, defineComponent, h, nextTick } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { createConversation, getConversation, listConversations, updateConversationKnowledgeBases } from '@/api/conversation';
import { listKnowledgeBases } from '@/api/knowledge';
import { streamChat } from '@/api/chat';
import { useChatStore } from '@/stores/chat';
import { useModelSettingStore } from '@/stores/model-setting';
import type { Conversation } from '@/types/chat';
import ChatView from './ChatView.vue';

vi.mock('@/api/conversation', () => ({
  createConversation: vi.fn(), getConversation: vi.fn(), listConversations: vi.fn(),
  deleteConversation: vi.fn(), updateConversationKnowledgeBases: vi.fn(),
}));
vi.mock('@/api/knowledge', () => ({ listKnowledgeBases: vi.fn() }));
vi.mock('@/api/chat', () => ({ streamChat: vi.fn(), interruptTask: vi.fn(), completeBrowserScreenshot: vi.fn() }));
vi.mock('@/components/chat/ChatComposer.vue', () => ({ default: defineComponent({
  emits: ['send'], setup: (_, { emit, slots }) => () => h('div', { class: 'chat-composer' }, [
    slots.context?.({ disabled: false }),
    h('div', { class: 'composer-footer' }, [
      slots.tools?.({ disabled: false }),
      h('button', { 'data-testid': 'send', onClick: () => emit('send', '查询业务资料') }, '发送'),
    ]),
  ]),
}) }));
vi.mock('@/components/chat/ChatTimeline.vue', () => ({ default: defineComponent({ setup: () => () => h('div') }) }));
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn(), warning: vi.fn() } }));

const cleanups: (() => void)[] = [];
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(listKnowledgeBases).mockResolvedValue([
    { id: 'common', name: '公共知识库', description: '', isDefault: true },
    { id: 'rules', name: '规则文档', description: '', isDefault: false },
    { id: 'product', name: '产品资料', description: '', isDefault: false },
  ]);
  vi.mocked(listConversations).mockResolvedValue([]);
  vi.mocked(getConversation).mockResolvedValue(conversation('history', ['product']));
  vi.mocked(createConversation).mockImplementation(async (ids = []) => conversation('new-session', ids));
  vi.mocked(updateConversationKnowledgeBases).mockImplementation(async (sessionId, ids) => conversation(sessionId, ids));
  vi.mocked(streamChat).mockImplementation(async (request, onEvent) => {
    onEvent({ eventType: 'task_end', taskId: 'task-1', sessionId: request.sessionId, userId: 'user-1', structuredData: {} });
  });
});
afterEach(() => cleanups.splice(0).forEach((cleanup) => cleanup()));

describe('chat knowledge source selection', () => {
  it('shows automatic common knowledge and creates the first conversation with multiple selected sources', async () => {
    const { container, chat } = await mountView('/chat');
    expect(container.textContent).toContain('公共知识库 · 自动关联');
    expect(container.querySelector('.knowledge-sources')).toBeNull();
    expect(container.querySelector('.composer-footer button[aria-label="关联知识库"]')).not.toBeNull();
    await openPicker(container);
    expect(container.querySelector('select, [role="combobox"]')).toBeNull();
    const common = checkbox(container, '公共知识库，自动关联');
    expect(common.checked).toBe(true);
    expect(common.disabled).toBe(true);
    checkbox(container, '规则文档').click();
    await settle();
    checkbox(container, '产品资料').click();
    await settle();
    expect(chat.selectedKnowledgeBaseIds).toEqual(['rules', 'product']);
    expect(container.querySelector('.composer-knowledge-context')?.textContent).toContain('规则文档');
    expect(container.querySelector('.composer-knowledge-context')?.textContent).toContain('产品资料');
    container.querySelector<HTMLButtonElement>('[data-testid="send"]')!.click();
    await vi.waitFor(() => expect(streamChat).toHaveBeenCalledTimes(1));
    expect(createConversation).toHaveBeenCalledWith(['rules', 'product']);
    expect(chat.currentSessionId).toBe('new-session');
  });

  it('restores an existing selection and saves a replacement before exposing it as selected', async () => {
    const { container, chat } = await mountView('/chat/history');
    expect(container.querySelector('.composer-knowledge-context')?.textContent).toContain('产品资料');
    await openPicker(container);
    expect(checkbox(container, '产品资料').checked).toBe(true);
    checkbox(container, '产品资料').click();
    await settle();
    checkbox(container, '规则文档').click();
    await settle();
    expect(updateConversationKnowledgeBases).toHaveBeenCalledWith('history', ['rules']);
    expect(chat.selectedKnowledgeBaseIds).toEqual(['rules']);
  });

  it('keeps the picker open while an association saves and displays the old selection on failure', async () => {
    let reject!: (error: Error) => void;
    vi.mocked(updateConversationKnowledgeBases).mockReturnValueOnce(new Promise((_, fail) => { reject = fail; }));
    const { container, chat } = await mountView('/chat/history');
    await openPicker(container);

    checkbox(container, '规则文档').click();
    await settle();
    expect(container.querySelector('[role="dialog"]')).not.toBeNull();
    expect(checkbox(container, '规则文档').disabled).toBe(true);
    expect(checkbox(container, '规则文档').checked).toBe(false);
    expect(chat.selectedKnowledgeBaseIds).toEqual(['product']);
    reject(new Error('保存失败'));
    await settle();

    expect(container.querySelector('[role="dialog"]')).not.toBeNull();
    expect(checkbox(container, '规则文档').checked).toBe(false);
    expect(checkbox(container, '规则文档').disabled).toBe(false);
    expect(checkbox(container, '产品资料').checked).toBe(true);
    expect(chat.selectedKnowledgeBaseIds).toEqual(['product']);
  });

  it('removes an optional source directly from the composer while keeping common knowledge automatic', async () => {
    const { container, chat } = await mountView('/chat/history');

    container.querySelector<HTMLButtonElement>('button[aria-label="移除知识库 产品资料"]')!.click();
    await settle();

    expect(updateConversationKnowledgeBases).toHaveBeenCalledWith('history', []);
    expect(chat.selectedKnowledgeBaseIds).toEqual([]);
    expect(container.querySelector('.composer-knowledge-context')?.textContent).toContain('公共知识库 · 自动关联');
    expect(container.querySelector('button[aria-label="移除知识库 公共知识库"]')).toBeNull();
  });
});

async function mountView(path: string) {
  const container = document.createElement('div');
  document.body.append(container);
  const pinia = createPinia();
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/chat/:sessionId?', component: ChatView }] });
  await router.push(path);
  await router.isReady();
  const settings = useModelSettingStore(pinia);
  settings.loaded = true;
  settings.models = [{ id: 'model-1', name: '测试模型', modelName: 'model', modelUrl: 'https://example.com/v1',
    contextWindowSize: 32768, temperature: null, isDefault: true }];
  const app = createApp(ChatView).use(pinia).use(router);
  app.component('el-tooltip', defineComponent({ setup: (_, { slots }) => () => slots.default?.() }));
  app.component('el-icon', defineComponent({ setup: (_, { slots }) => () => h('span', slots.default?.()) }));
  app.mount(container);
  cleanups.push(() => { app.unmount(); container.remove(); });
  await settle();
  return { container, chat: useChatStore(pinia) };
}

async function openPicker(container: HTMLElement): Promise<void> {
  container.querySelector<HTMLButtonElement>('button[aria-label="关联知识库"]')!.click();
  await settle();
}

function checkbox(container: HTMLElement, name: string): HTMLInputElement {
  return container.querySelector<HTMLInputElement>(`input[aria-label="${name}"]`)!;
}

async function settle(): Promise<void> {
  for (let index = 0; index < 12; index++) await nextTick();
}

function conversation(sessionId: string, knowledgeBaseIds: string[]): Conversation {
  return { sessionId, knowledgeBaseIds, title: '对话', createdAt: null, updatedAt: null, messages: [], files: [] };
}
