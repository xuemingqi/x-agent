// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createApp, defineComponent, h, nextTick, reactive } from 'vue';

import ChatComposer from './ChatComposer.vue';
import type { QueuedMessage } from '@/stores/chat';

vi.mock('@/api/skill', () => ({ listSkills: vi.fn().mockResolvedValue([]) }));

const cleanups: (() => void)[] = [];

afterEach(() => cleanups.splice(0).forEach((cleanup) => cleanup()));

function mountComposer() {
  const state = reactive({ sending: true, interrupting: false, queuePaused: false, queuedMessages: [] as QueuedMessage[] });
  const onSend = vi.fn();
  const onSteer = vi.fn();
  const onStop = vi.fn();
  const onEditQueuedMessage = vi.fn((id: string) => {
    const message = state.queuedMessages.find((item) => item.id === id)!;
    message.draft = message.content;
  });
  const onUpdateQueuedDraft = vi.fn((id: string, content: string) => {
    state.queuedMessages.find((item) => item.id === id)!.draft = content;
  });
  const onFinishQueuedEdit = vi.fn((id: string, save: boolean) => {
    const message = state.queuedMessages.find((item) => item.id === id)!;
    if (save) message.content = message.draft!;
    delete message.draft;
  });
  const onRemoveQueuedMessage = vi.fn((id: string) => {
    state.queuedMessages = state.queuedMessages.filter((item) => item.id !== id);
  });
  const container = document.createElement('div');
  document.body.append(container);
  const app = createApp({
    render: () => h(ChatComposer, {
      models: [], modelId: 'model-1', modelConfigured: true, files: [], uploadingFiles: false,
      ...state, onSend, onSteer, onStop, onEditQueuedMessage, onUpdateQueuedDraft,
      onFinishQueuedEdit, onRemoveQueuedMessage,
    }),
  });
  const stub = defineComponent({ setup: (_, { slots }) => () => h('div', slots.default?.()) });
  for (const name of ['el-icon', 'el-select', 'el-option', 'el-button']) app.component(name, stub);
  app.mount(container);
  cleanups.push(() => { app.unmount(); container.remove(); });
  return { state, onSend, onSteer, onStop, container, onEditQueuedMessage,
    onUpdateQueuedDraft, onFinishQueuedEdit, onRemoveQueuedMessage };
}

describe('ChatComposer queued messages', () => {
  it('stacks more than two messages and collapses on mouse leave or outside pointer input', async () => {
    const { state, container, onSteer } = mountComposer();
    state.queuedMessages = Array.from({ length: 8 }, (_, index) => ({
      id: `q${index}`, content: `待发送 ${index}`, modelId: 'model-1', files: [],
    }));
    await nextTick();
    const queue = container.querySelector<HTMLElement>('.composer-queue')!;
    const toggle = container.querySelector<HTMLButtonElement>('.composer-queue-toggle')!;
    expect(toggle.textContent).toContain('8 条待发送');
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(container.querySelector('.composer-queue-list')).toBeNull();

    toggle.click();
    await nextTick();
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(container.querySelectorAll('.composer-queue-row')).toHaveLength(8);
    const steer = container.querySelector<HTMLButtonElement>('.composer-steer-button')!;
    steer.dispatchEvent(new Event('pointerdown', { bubbles: true }));
    steer.click();
    await nextTick();
    expect(onSteer).toHaveBeenCalledExactlyOnceWith('q0');
    expect(toggle.getAttribute('aria-expanded')).toBe('true');

    queue.dispatchEvent(new MouseEvent('mouseleave'));
    await nextTick();
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    toggle.click();
    await nextTick();
    container.querySelector('textarea')!.dispatchEvent(new Event('pointerdown', { bubbles: true }));
    await nextTick();
    expect(toggle.getAttribute('aria-expanded')).toBe('false');

    state.queuedMessages = state.queuedMessages.slice(0, 2);
    await nextTick();
    expect(container.querySelector('.composer-queue-toggle')).toBeNull();
    expect(container.querySelectorAll('.composer-queue-row')).toHaveLength(2);
  });

  it('keeps the main-composer edit when the expanded queue collapses', async () => {
    const { state, container } = mountComposer();
    state.queuedMessages = Array.from({ length: 3 }, (_, index) => ({
      id: `q${index}`, content: `待发送 ${index}`, modelId: 'model-1', files: [],
    }));
    await nextTick();
    container.querySelector<HTMLButtonElement>('.composer-queue-toggle')!.click();
    await nextTick();
    container.querySelectorAll<HTMLButtonElement>('[title="编辑待发送消息"]')[1].click();
    await nextTick();
    container.querySelector('.composer-queue')!.dispatchEvent(new MouseEvent('mouseleave'));
    await nextTick();
    const editor = container.querySelector<HTMLTextAreaElement>('textarea')!;
    expect(editor.value).toBe('待发送 1');
    editor.value = '编辑后';
    editor.dispatchEvent(new Event('input', { bubbles: true }));
    await nextTick();
    container.querySelector<HTMLButtonElement>('[title="保存修改"]')!.click();
    expect(state.queuedMessages[1].content).toBe('编辑后');
    expect(state.queuedMessages.map((message) => message.id)).toEqual(['q0', 'q1', 'q2']);
  });

  it('allows repeated sends while answering and keeps the stop button available', async () => {
    const { container, onSend, onStop } = mountComposer();
    const textarea = container.querySelector('textarea')!;
    for (const content of ['补充背景', '换一个方向']) {
      textarea.value = content;
      textarea.dispatchEvent(new Event('input', { bubbles: true }));
      await nextTick();
      container.querySelector<HTMLButtonElement>('[title="加入待发送"]')!.click();
      await nextTick();
      expect(textarea.value).toBe('');
    }
    expect(onSend.mock.calls).toEqual([['补充背景'], ['换一个方向']]);
    container.querySelector<HTMLButtonElement>('[title="停止"]')!.click();
    expect(onStop).toHaveBeenCalledOnce();
    expect(container.querySelector<HTMLButtonElement>('[title="上传会话文件"]')!.disabled).toBe(false);
  });

  it('renders queued messages above the input and steers the selected message', async () => {
    const { state, container, onSteer } = mountComposer();
    state.queuedMessages = [
      { id: 'q1', content: '第一条补充', modelId: 'model-1', files: [] },
      { id: 'q2', content: '请优先完成测试', modelId: 'model-1', files: [] },
    ];
    await nextTick();
    const queue = container.querySelector('.composer-queue')!;
    const composer = container.querySelector('.chat-composer')!;
    expect(queue.compareDocumentPosition(composer) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    const buttons = container.querySelectorAll<HTMLButtonElement>('.composer-steer-button');
    expect(buttons[1].textContent).toContain('调整方向');
    buttons[1].click();
    expect(onSteer).toHaveBeenCalledExactlyOnceWith('q2');

    state.interrupting = true;
    await nextTick();
    expect(buttons[0].disabled).toBe(true);
    expect(buttons[1].disabled).toBe(true);
    expect(buttons[0].title).toContain('正在结束当前回答');
    expect(container.querySelector<HTMLButtonElement>('[title="停止"]')!.disabled).toBe(true);
  });

  it('offers explicit continuation for paused messages after stopping', async () => {
    const { state, container, onSteer } = mountComposer();
    state.sending = false;
    state.queuePaused = true;
    state.queuedMessages = [{ id: 'q1', content: '稍后继续', modelId: 'model-1', files: [] }];
    await nextTick();
    const button = container.querySelector<HTMLButtonElement>('.composer-steer-button')!;
    expect(button.textContent).toContain('立即发送');
    expect(container.querySelector('[title="队列已暂停"]')).not.toBeNull();
    button.click();
    expect(onSteer).toHaveBeenCalledExactlyOnceWith('q1');
  });

  it('edits in the main composer and restores its draft after saving or deleting', async () => {
    const { state, container, onSend, onUpdateQueuedDraft, onFinishQueuedEdit, onRemoveQueuedMessage } = mountComposer();
    state.queuedMessages = [{ id: 'q1', content: '待修改的内容', modelId: 'model-1', files: [] }];
    const composerInput = container.querySelector<HTMLTextAreaElement>('.chat-composer textarea')!;
    composerInput.value = '尚未提交的草稿';
    composerInput.dispatchEvent(new Event('input', { bubbles: true }));
    await nextTick();
    container.querySelector<HTMLButtonElement>('[title="编辑待发送消息"]')!.click();
    await nextTick();
    await nextTick();
    const editor = container.querySelector<HTMLTextAreaElement>('[aria-label="编辑待发送消息内容"]')!;
    expect(editor).toBe(composerInput);
    expect(container.querySelectorAll('textarea')).toHaveLength(1);
    expect(editor.value).toBe('待修改的内容');
    expect(document.activeElement).toBe(editor);
    expect(container.querySelector<HTMLButtonElement>('.composer-steer-button')!.disabled).toBe(true);
    editor.value = '新的内容';
    editor.dispatchEvent(new Event('input', { bubbles: true }));
    await nextTick();
    expect(onUpdateQueuedDraft).toHaveBeenCalledWith('q1', '新的内容');
    container.querySelector<HTMLButtonElement>('[title="保存修改"]')!.click();
    expect(onFinishQueuedEdit).toHaveBeenCalledExactlyOnceWith('q1', true);
    await nextTick();
    expect(state.queuedMessages[0].content).toBe('新的内容');
    expect(composerInput.value).toBe('尚未提交的草稿');
    expect(onSend).not.toHaveBeenCalled();
    container.querySelector<HTMLButtonElement>('[title="编辑待发送消息"]')!.click();
    await nextTick();
    container.querySelector<HTMLButtonElement>('[title="删除待发送消息"]')!.click();
    expect(onRemoveQueuedMessage).toHaveBeenCalledExactlyOnceWith('q1');
    await nextTick();
    expect(composerInput.value).toBe('尚未提交的草稿');
  });

  it('disables empty edits and ignores save shortcuts during IME composition', async () => {
    const { state, container, onFinishQueuedEdit } = mountComposer();
    state.queuedMessages = [{ id: 'q1', content: '原文', draft: '', modelId: 'model-1', files: [] }];
    await nextTick();
    const editor = container.querySelector<HTMLTextAreaElement>('.chat-composer textarea')!;
    expect(container.querySelector<HTMLButtonElement>('[title="保存修改"]')!.disabled).toBe(true);
    editor.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', isComposing: true, bubbles: true }));
    expect(onFinishQueuedEdit).not.toHaveBeenCalled();
    editor.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    expect(onFinishQueuedEdit).toHaveBeenCalledExactlyOnceWith('q1', false);
    await nextTick();
    expect(state.queuedMessages[0].content).toBe('原文');
    expect(editor.value).toBe('');
  });
});
