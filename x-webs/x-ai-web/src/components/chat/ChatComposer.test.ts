// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createApp, defineComponent, h, nextTick } from 'vue';

import ChatComposer from './ChatComposer.vue';

vi.mock('@/api/skill', () => ({ listSkills: vi.fn().mockResolvedValue([]) }));

const cleanups: (() => void)[] = [];

afterEach(() => {
  cleanups.splice(0).forEach((cleanup) => cleanup());
});

function mountComposer(overrides: { sending?: boolean; uploadingFiles?: boolean } = {}) {
  const onSend = vi.fn();
  const onUpload = vi.fn();
  const container = document.createElement('div');
  document.body.append(container);
  const app = createApp(ChatComposer, {
    models: [],
    modelId: 'test-model',
    modelConfigured: true,
    sending: false,
    files: [],
    uploadingFiles: false,
    ...overrides,
    onSend,
    onUpload,
  });
  const stub = defineComponent({ setup: (_, { slots }) => () => h('div', slots.default?.()) });
  for (const name of ['el-icon', 'el-select', 'el-option', 'el-button']) {
    app.component(name, stub);
  }
  app.mount(container);
  cleanups.push(() => {
    app.unmount();
    container.remove();
  });
  return {
    onSend,
    onUpload,
    textarea: container.querySelector('textarea')!,
    sendButton: () => container.querySelector<HTMLButtonElement>('button[title="发送"]')!,
  };
}

function input(textarea: HTMLTextAreaElement, value: string, isComposing = false): void {
  textarea.value = value;
  textarea.dispatchEvent(new InputEvent('input', { bubbles: true, isComposing }));
}

function composition(textarea: HTMLTextAreaElement, type: 'compositionstart' | 'compositionend'): void {
  textarea.dispatchEvent(new CompositionEvent(type, { bubbles: true }));
}

describe('ChatComposer input method handling', () => {
  it('uploads a pasted screenshot without submitting or replacing the text draft', async () => {
    const { textarea, onSend, onUpload } = mountComposer();
    input(textarea, '描述这张图片');
    const file = new File(['image'], 'screenshot.png', { type: 'image/png' });
    const paste = new Event('paste', { bubbles: true, cancelable: true });
    Object.defineProperty(paste, 'clipboardData', {
      value: { items: [{ kind: 'file', type: 'image/png', getAsFile: () => file }] },
    });
    textarea.dispatchEvent(paste);
    await nextTick();

    expect(onUpload).toHaveBeenCalledExactlyOnceWith([file]);
    expect(onSend).not.toHaveBeenCalled();
    expect(textarea.value).toBe('描述这张图片');
    expect(paste.defaultPrevented).toBe(true);
  });

  it('keeps normal text paste and refuses screenshot uploads while another upload is running', () => {
    const { textarea, onUpload } = mountComposer({ uploadingFiles: true });
    const paste = new Event('paste', { bubbles: true, cancelable: true });
    textarea.dispatchEvent(paste);
    expect(paste.defaultPrevented).toBe(false);
    expect(onUpload).not.toHaveBeenCalled();
  });

  it('sends normal text and clears the input', async () => {
    const { textarea, sendButton, onSend } = mountComposer();
    input(textarea, '你好');
    await nextTick();
    sendButton().click();
    await nextTick();

    expect(onSend).toHaveBeenCalledExactlyOnceWith('你好');
    expect(textarea.value).toBe('');
    expect(sendButton().disabled).toBe(true);
  });

  it.each(['', '你好'])('waits for composition to finish before sending a draft starting with "%s"', async (prefix) => {
    const { textarea, sendButton, onSend } = mountComposer();
    textarea.focus();
    input(textarea, prefix);
    composition(textarea, 'compositionstart');
    input(textarea, `${prefix}shi`, true);
    await nextTick();

    expect(sendButton().disabled).toBe(false);
    sendButton().click();
    expect(onSend).not.toHaveBeenCalled();
    expect(document.activeElement).not.toBe(textarea);

    textarea.value = `${prefix}是`;
    composition(textarea, 'compositionend');
    input(textarea, `${prefix}是`);
    await nextTick();
    await nextTick();

    expect(onSend).toHaveBeenCalledExactlyOnceWith(`${prefix}是`);
    expect(textarea.value).toBe('');
    expect(sendButton().disabled).toBe(true);
    input(textarea, '下一条');
    await nextTick();
    expect(textarea.value).toBe('下一条');
    expect(onSend).toHaveBeenCalledTimes(1);
  });

  it('sends the committed text when composition ends before the button click', async () => {
    const { textarea, sendButton, onSend } = mountComposer();
    input(textarea, '你好');
    composition(textarea, 'compositionstart');
    input(textarea, '你好shi', true);
    await nextTick();
    textarea.value = '你好是';
    composition(textarea, 'compositionend');
    input(textarea, '你好是');
    sendButton().click();
    await nextTick();

    expect(onSend).toHaveBeenCalledExactlyOnceWith('你好是');
    expect(textarea.value).toBe('');
  });

  it('handles change as a composition completion fallback without sending twice', async () => {
    const { textarea, sendButton, onSend } = mountComposer();
    composition(textarea, 'compositionstart');
    input(textarea, 'ni', true);
    await nextTick();
    sendButton().click();
    textarea.value = '你';
    textarea.dispatchEvent(new Event('change', { bubbles: true }));
    composition(textarea, 'compositionend');
    await nextTick();
    await nextTick();

    expect(onSend).toHaveBeenCalledExactlyOnceWith('你');
    expect(textarea.value).toBe('');
  });

  it.each([
    { isComposing: true },
    { keyCode: 229 },
    {},
  ])('does not send or prevent candidate confirmation with %j', async (keyboardOptions) => {
    const { textarea, onSend } = mountComposer();
    input(textarea, '你好');
    if (!Object.keys(keyboardOptions).length) {
      composition(textarea, 'compositionstart');
    }
    const event = new KeyboardEvent('keydown', {
      key: 'Enter', bubbles: true, cancelable: true, ...keyboardOptions,
    });
    textarea.dispatchEvent(event);
    await nextTick();

    expect(onSend).not.toHaveBeenCalled();
    expect(event.defaultPrevented).toBe(false);
    expect(textarea.value).toBe('你好');
  });

  it('keeps Shift+Enter for newlines and sends on Enter after composition', async () => {
    const { textarea, onSend } = mountComposer();
    composition(textarea, 'compositionstart');
    textarea.value = '你好';
    composition(textarea, 'compositionend');
    const newline = new KeyboardEvent('keydown', {
      key: 'Enter', shiftKey: true, bubbles: true, cancelable: true,
    });
    textarea.dispatchEvent(newline);
    expect(newline.defaultPrevented).toBe(false);
    expect(onSend).not.toHaveBeenCalled();
    textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    await nextTick();

    expect(onSend).toHaveBeenCalledExactlyOnceWith('你好');
    expect(textarea.value).toBe('');
  });

  it('does not send an empty draft after composition is cancelled', async () => {
    const { textarea, sendButton, onSend } = mountComposer();
    composition(textarea, 'compositionstart');
    input(textarea, 'ni', true);
    await nextTick();
    sendButton().click();
    textarea.value = '';
    composition(textarea, 'compositionend');
    await nextTick();
    await nextTick();

    expect(onSend).not.toHaveBeenCalled();
    expect(textarea.value).toBe('');
    expect(sendButton().disabled).toBe(true);
  });
});
