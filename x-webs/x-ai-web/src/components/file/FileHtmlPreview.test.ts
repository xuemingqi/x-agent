// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createApp, h, nextTick, ref } from 'vue';

import { downloadConversationFile } from '@/api/conversation-file';
import type { ConversationFile } from '@/types/chat';
import FileHtmlPreview from './FileHtmlPreview.vue';

vi.mock('@/api/conversation-file', () => ({ downloadConversationFile: vi.fn() }));
const cleanups: (() => void)[] = [];
afterEach(() => { cleanups.splice(0).forEach((cleanup) => cleanup()); vi.resetAllMocks(); });

describe('HTML runtime preview', () => {
  it('loads through the authenticated API and runs inline code in an isolated iframe', async () => {
    vi.mocked(downloadConversationFile).mockResolvedValue({ size: 100, text: async () => `
      <html><head><base href="https://example.org"><meta http-equiv="refresh" content="0;url=https://example.org"></head>
      <body><h1>Demo</h1><script>document.body.dataset.ready = 'yes'</script></body></html>` } as Blob);
    const { container } = mount();
    await vi.waitFor(() => expect(container.querySelector('iframe')).not.toBeNull());
    const iframe = container.querySelector('iframe')!;
    expect(downloadConversationFile).toHaveBeenCalledWith('session-1', 'first');
    expect(iframe.getAttribute('sandbox')).toBe('allow-scripts');
    expect(iframe.getAttribute('allow')).toContain("display-capture 'none'");
    const html = new DOMParser().parseFromString(iframe.srcdoc, 'text/html');
    expect(html.querySelector('base')).toBeNull();
    expect(html.head.firstElementChild?.getAttribute('http-equiv')).toBe('Content-Security-Policy');
    expect(html.head.firstElementChild?.getAttribute('content')).toContain("connect-src 'none'");
    expect(html.querySelector('script')?.textContent).toContain('dataset.ready');
    expect(html.querySelector('meta[http-equiv="refresh"]')).toBeNull();
  });

  it('discards stale downloads when a newly published HTML replaces the preview', async () => {
    let finish!: (blob: Blob) => void;
    vi.mocked(downloadConversationFile).mockReturnValueOnce(new Promise((resolve) => { finish = resolve; }))
      .mockResolvedValueOnce({ size: 10, text: async () => '<h1>New</h1>' } as Blob);
    const { container, file } = mount();
    file.value = { ...file.value, id: 'second' };
    await nextTick();
    await vi.waitFor(() => expect(container.querySelector('iframe')?.srcdoc).toContain('New'));
    finish({ size: 10, text: async () => '<h1>Old</h1>' } as Blob);
    await Promise.resolve();
    await nextTick();
    expect(container.querySelector('iframe')?.srcdoc).not.toContain('Old');
  });

  it('keeps the running page when a screenshot request repeats metadata for the same file', async () => {
    vi.mocked(downloadConversationFile).mockResolvedValue({ size: 10, text: async () => '<h1>Running</h1>' } as Blob);
    const { container, file } = mount();
    await vi.waitFor(() => expect(container.querySelector('iframe')).not.toBeNull());
    const iframe = container.querySelector('iframe');
    file.value = { ...file.value };
    await nextTick();
    expect(downloadConversationFile).toHaveBeenCalledTimes(1);
    expect(container.querySelector('iframe')).toBe(iframe);
  });
});

function mount() {
  const file = ref({ id: 'first', sessionId: 'session-1', name: 'demo.html', sizeBytes: 100 } as ConversationFile);
  const container = document.createElement('div');
  const app = createApp({ setup: () => () => h(FileHtmlPreview, { file: file.value }) });
  app.mount(container);
  cleanups.push(() => app.unmount());
  return { container, file };
}
