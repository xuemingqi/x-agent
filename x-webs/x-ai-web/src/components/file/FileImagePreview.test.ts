// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createApp, defineComponent, h, nextTick } from 'vue';

import { downloadConversationFile } from '@/api/conversation-file';
import type { ConversationFile } from '@/types/chat';
import FileImagePreview from './FileImagePreview.vue';

vi.mock('@/api/conversation-file', () => ({ downloadConversationFile: vi.fn() }));

const cleanups: (() => void)[] = [];

afterEach(() => {
  cleanups.splice(0).forEach((cleanup) => cleanup());
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

function mountPreview() {
  const container = document.createElement('div');
  const app = createApp(FileImagePreview, {
    file: { id: 'file-1', sessionId: 'session-1', name: 'image.png' } as ConversationFile,
  });
  app.component('el-image', defineComponent({
    props: ['src', 'alt'],
    setup: (props) => () => h('img', { src: props.src, alt: props.alt }),
  }));
  app.mount(container);
  const unmount = () => app.unmount();
  cleanups.push(unmount);
  return { container, unmount };
}

describe('authenticated image previews', () => {
  it('uses the file API and releases the blob URL when unmounted', async () => {
    const revokeObjectURL = vi.fn();
    vi.stubGlobal('URL', { createObjectURL: vi.fn(() => 'blob:preview'), revokeObjectURL });
    vi.mocked(downloadConversationFile).mockResolvedValue(new Blob(['image'], { type: 'image/png' }));
    const { container, unmount } = mountPreview();
    await Promise.resolve();
    await nextTick();

    expect(downloadConversationFile).toHaveBeenCalledWith('session-1', 'file-1');
    expect(container.querySelector('img')?.getAttribute('src')).toBe('blob:preview');
    unmount();
    cleanups.pop();
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:preview');
  });

  it('does not create a blob URL if the component was removed before download completed', async () => {
    let finish!: (blob: Blob) => void;
    vi.mocked(downloadConversationFile).mockReturnValue(new Promise((resolve) => { finish = resolve; }));
    const createObjectURL = vi.fn();
    vi.stubGlobal('URL', { createObjectURL, revokeObjectURL: vi.fn() });
    const { unmount } = mountPreview();
    unmount();
    cleanups.pop();
    finish(new Blob(['image']));
    await Promise.resolve();
    expect(createObjectURL).not.toHaveBeenCalled();
  });
});
