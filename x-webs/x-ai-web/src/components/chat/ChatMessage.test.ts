// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createApp, defineComponent, h, nextTick, reactive } from 'vue';

import type { DisplayMessage } from '@/stores/chat';
import type { BrowserScreenshotRequest } from '@/types/chat';
import ChatMessage from './ChatMessage.vue';

vi.mock('@/stores/chat', () => ({
  useChatStore: () => ({ respondToScreenshot: vi.fn(), openHtmlPreview: vi.fn() }),
}));
vi.mock('@/api/conversation-file', () => ({ downloadConversationFile: vi.fn() }));
const cleanups: (() => void)[] = [];
afterEach(() => cleanups.splice(0).forEach((cleanup) => cleanup()));

describe('ChatMessage screenshot timeline', () => {
  it('keeps consent visible at its original position while later content streams below it', async () => {
    const { message, container } = mountMessage();
    const request = container.querySelector('.screenshot-card')!;
    const earlier = container.querySelector('.reasoning-panel')!;
    expect(earlier.compareDocumentPosition(request) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(container.querySelector('.reasoning-content')?.getAttribute('style')).toContain('display: none');
    expect(container.querySelector('[aria-label="同意截图并发送"]')).not.toBeNull();

    message.pendingText = '收到截图后继续分析';
    await nextTick();
    const later = container.querySelector('.reasoning-text.active')!;
    expect(request.compareDocumentPosition(later) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(container.querySelector('.screenshot-card')).toBe(request);

    message.screenshots![0].status = 'received';
    message.pendingText = '';
    message.content = '最终分析：棋盘布局正常';
    message.processing = false;
    await nextTick();
    const answer = container.querySelector('.final-answer')!;
    expect(request.compareDocumentPosition(answer) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(container.querySelector('.screenshot-details')).toBeNull();
    expect(container.querySelector('.screenshot-summary')?.textContent).toContain('截图已发送给 AI');
    expect(container.querySelector('[aria-label="同意截图并发送"]')).toBeNull();
  });

  it.each(['received', 'declined', 'expired', 'cancelled'] as const)('collapses %s requests and lets the user expand their details', async (status) => {
    const { message, container } = mountMessage();
    message.screenshots![0].status = status;
    await nextTick();
    const toggle = container.querySelector<HTMLButtonElement>('.screenshot-summary')!;
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(container.querySelector('.screenshot-details')).toBeNull();
    toggle.click();
    await nextTick();
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(container.querySelector('.screenshot-details')?.textContent).toContain('确认棋盘布局');
    expect(container.querySelector('[aria-label="查看预览"]')).not.toBeNull();
    toggle.click();
    await nextTick();
    expect(container.querySelector('.screenshot-details')).toBeNull();
  });

  it('places repeated screenshot requests between their respective processing steps', async () => {
    const { message, container } = mountMessage();
    message.screenshots![0].status = 'received';
    message.reasoning!.push({ id: 'second-step', type: 'text', content: '调整棋盘后再次检查' });
    message.screenshots!.push({ ...screenshotRequest(), requestId: 'capture-2', reasoningOffset: 2 });
    message.pendingText = '继续检查新版本';
    await nextTick();
    const sequence = [...container.querySelector('.assistant-response')!.children].map((node) => node.className);
    expect(sequence).toEqual([
      'reasoning-panel', expect.stringContaining('screenshot-card'),
      'reasoning-panel', expect.stringContaining('screenshot-card'), 'reasoning-panel',
    ]);
  });
});

function screenshotRequest(): BrowserScreenshotRequest {
  return {
    requestId: 'capture-1', taskId: 'task-1', sessionId: 'session-1', reason: '确认棋盘布局',
    expiresAt: Date.now() + 180000, requestedAt: Date.now(), reasoningOffset: 1,
    status: 'pending', files: [],
    previewFile: { id: '20', sessionId: 'session-1', messageId: '10', kind: 2, name: '五子棋.html',
      contentType: 'text/html', sizeBytes: 100, workspacePath: 'output/game.html', createdAt: null },
  };
}

function mountMessage() {
  const message = reactive<DisplayMessage>({
    id: 'assistant', role: 'ASSISTANT', kind: 'message', content: '', processing: true,
    reasoning: [{ id: 'first-step', type: 'text', content: '页面已生成，准备截图' }],
    screenshots: [screenshotRequest()],
  });
  const container = document.createElement('div');
  const app = createApp({ render: () => h(ChatMessage, { message }) });
  const stub = defineComponent({ setup: (_, { slots }) => () => h('div', slots.default?.()) });
  for (const name of ['el-icon', 'el-tooltip']) app.component(name, stub);
  app.mount(container);
  cleanups.push(() => app.unmount());
  return { message, container };
}
