// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createApp, defineComponent, h, nextTick, ref } from 'vue';

import {
  downloadKnowledgeDocument,
  downloadPublicKnowledgeDocument,
  getKnowledgeDocumentPreview,
  listKnowledgeDocumentChunks,
} from '@/api/knowledge';
import type { KnowledgeDocument, KnowledgeScope } from '@/types/knowledge';
import KnowledgeDocumentReader from './KnowledgeDocumentReader.vue';

vi.mock('@/api/knowledge', () => ({
  getKnowledgeDocumentPreview: vi.fn(),
  listKnowledgeDocumentChunks: vi.fn(),
  downloadKnowledgeDocument: vi.fn(),
  downloadPublicKnowledgeDocument: vi.fn(),
}));

const cleanups: (() => void)[] = [];
const file: KnowledgeDocument = {
  path: 'guide.pdf', name: 'guide.pdf', size: 100, contentType: 'application/pdf', updateTime: '',
  status: 'READY', chunkCount: 25, embeddingModel: 'test-embedding', errorMessage: null,
};

beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(getKnowledgeDocumentPreview).mockResolvedValue({
    path: file.path, name: file.name, content: '文档正文', contentType: file.contentType,
  });
  vi.mocked(listKnowledgeDocumentChunks).mockResolvedValue({
    items: [{ index: 1, content: '第一个切片', characterCount: 6 }], total: 25, page: 1, size: 20,
  });
  vi.stubGlobal('URL', {
    createObjectURL: vi.fn().mockReturnValue('blob:authenticated-pdf'),
    revokeObjectURL: vi.fn(),
  });
});

afterEach(() => {
  cleanups.splice(0).forEach((cleanup) => cleanup());
  vi.unstubAllGlobals();
});

function mount(initialTab: 'preview' | 'chunks' = 'preview', initialChunkIndex?: number, document = file) {
  const scope = ref<KnowledgeScope>('user');
  const onClose = vi.fn();
  const currentDocument = ref({ ...document });
  const container = window.document.createElement('div');
  const app = createApp({
    render: () => h(KnowledgeDocumentReader, { document: currentDocument.value, scope: scope.value, initialTab, initialChunkIndex, onClose }),
  });
  const wrapper = defineComponent({ setup: (_, { slots }) => () => h('div', slots.default?.()) });
  for (const name of ['el-icon', 'el-empty']) app.component(name, wrapper);
  app.component('el-button', defineComponent({ setup: (_, { slots }) => () => h('button', slots.default?.()) }));
  app.component('el-pagination', defineComponent({
    emits: ['update:current-page'],
    setup: (_, { emit }) => () => h('button', { onClick: () => emit('update:current-page', 2) }, '下一页'),
  }));
  app.directive('loading', {});
  app.mount(container);
  cleanups.push(() => app.unmount());
  return { container, scope, currentDocument, onClose };
}

function click(container: HTMLElement, label: string): void {
  [...container.querySelectorAll('button')].find((button) => button.textContent?.includes(label))!.click();
}

describe('knowledge document preview', () => {
  it('renders extracted HTML as text without executing uploaded markup', async () => {
    vi.mocked(getKnowledgeDocumentPreview).mockResolvedValue({
      path: file.path, name: file.name, content: '<script>stealToken()</script><img src=x onerror=stealToken()>',
      contentType: 'text/html',
    });
    const { container } = mount();
    await vi.waitFor(() => expect(container.querySelector('pre')?.textContent).toContain('<script>'));
    expect(container.querySelector('script, img')).toBeNull();
    expect(getKnowledgeDocumentPreview).toHaveBeenCalledWith('guide.pdf', 'user');
  });

  it('discards a private preview that completes after switching to public knowledge', async () => {
    let finish!: (value: Awaited<ReturnType<typeof getKnowledgeDocumentPreview>>) => void;
    vi.mocked(getKnowledgeDocumentPreview)
      .mockReturnValueOnce(new Promise((resolve) => { finish = resolve; }))
      .mockResolvedValueOnce({ path: file.path, name: file.name, content: '公共正文', contentType: 'text/plain' });
    const { container, scope } = mount();
    scope.value = 'public';
    await vi.waitFor(() => expect(container.querySelector('pre')?.textContent).toBe('公共正文'));
    finish({ path: file.path, name: file.name, content: '私有正文', contentType: 'text/plain' });
    await nextTick();
    await nextTick();
    expect(container.querySelector('pre')?.textContent).toBe('公共正文');
  });

  it('downloads a PDF through the authenticated API and revokes its preview URL on scope changes', async () => {
    vi.mocked(downloadKnowledgeDocument).mockResolvedValue(new Blob(['%PDF']));
    vi.mocked(downloadPublicKnowledgeDocument).mockResolvedValue(new Blob(['%PDF-public']));
    const { container, scope } = mount();
    click(container, '查看原始 PDF');
    await vi.waitFor(() => expect(container.querySelector('iframe')?.getAttribute('src')).toBe('blob:authenticated-pdf'));
    expect(downloadKnowledgeDocument).toHaveBeenCalledWith('guide.pdf');
    scope.value = 'public';
    await nextTick();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:authenticated-pdf');
    expect(downloadPublicKnowledgeDocument).toHaveBeenCalledWith('guide.pdf');
  });

  it('loads chunk pages on demand without truncating the displayed chunk content', async () => {
    vi.mocked(listKnowledgeDocumentChunks).mockResolvedValueOnce({
      items: [{ index: 1, content: '第一个切片', characterCount: 6 }], total: 25, page: 1, size: 20,
    }).mockResolvedValueOnce({
      items: [{ index: 21, content: '完整切片正文\n第二行', characterCount: 12 }], total: 25, page: 2, size: 20,
    });
    const { container } = mount();
    await nextTick();
    expect(listKnowledgeDocumentChunks).not.toHaveBeenCalled();
    click(container, '切片内容');
    await vi.waitFor(() => expect(container.querySelector('.chunk-card pre')?.textContent).toBe('第一个切片'));
    click(container, '下一页');
    await vi.waitFor(() => expect(container.querySelector('.chunk-card pre')?.textContent).toBe('完整切片正文\n第二行'));
    expect(listKnowledgeDocumentChunks).toHaveBeenLastCalledWith('guide.pdf', 'user', 2, 20);
    expect(container.querySelector('.chunk-card strong')?.textContent).toBe('切片 21');
  });

  it('opens and highlights a search hit beyond the first chunk page', async () => {
    vi.mocked(listKnowledgeDocumentChunks).mockResolvedValue({
      items: [{ index: 23, content: '命中的文档片段', characterCount: 8 }], total: 25, page: 2, size: 20,
    });
    const { container } = mount('chunks', 23);
    await vi.waitFor(() => expect(container.querySelector('.chunk-card.matched pre')?.textContent).toBe('命中的文档片段'));
    expect(listKnowledgeDocumentChunks).toHaveBeenCalledWith('guide.pdf', 'user', 2, 20);
  });

  it('renders as a named page region and exposes a back action without a modal drawer', async () => {
    const { container, onClose } = mount();
    await nextTick();
    expect(container.querySelector('section.document-reader[aria-label="文档阅读区"]')).not.toBeNull();
    expect(container.querySelector('el-drawer, .el-drawer, .el-overlay, [role="dialog"]')).toBeNull();
    click(container, '文档列表');
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('refreshes the selected document content after reindexing without opening a new overlay', async () => {
    const { container, currentDocument } = mount();
    await vi.waitFor(() => expect(container.querySelector('pre')?.textContent).toBe('文档正文'));
    vi.mocked(getKnowledgeDocumentPreview).mockResolvedValue({
      path: file.path, name: file.name, content: '重新索引后的正文', contentType: file.contentType,
    });
    currentDocument.value = { ...file, updateTime: '2026-11-09 18:00:00' };
    await vi.waitFor(() => expect(container.querySelector('pre')?.textContent).toBe('重新索引后的正文'));
  });

  it('renders Markdown headings and lists while keeping embedded HTML escaped', async () => {
    const markdownFile = { ...file, path: 'guide.MD', name: 'guide.MD', contentType: 'text/markdown' };
    vi.mocked(getKnowledgeDocumentPreview).mockResolvedValue({
      path: markdownFile.path, name: markdownFile.name, contentType: 'text/markdown',
      content: '# 接入规范\n\n## 发布检查\n\n- 核对审批\n- 准备回滚\n\n<script>stealToken()</script>\n\n<img src=x onerror=stealToken()>',
    });
    const { container } = mount('preview', undefined, markdownFile);
    await vi.waitFor(() => expect(container.querySelector('.markdown-preview h1')?.textContent).toBe('接入规范'));
    expect(container.querySelector('.markdown-preview h2')?.textContent).toBe('发布检查');
    expect([...container.querySelectorAll('.markdown-preview li')].map((item) => item.textContent)).toEqual(['核对审批', '准备回滚']);
    expect(container.querySelector('.markdown-preview')?.textContent).toContain('<script>stealToken()</script>');
    expect(container.querySelector('.markdown-preview script, .markdown-preview img, [onerror]')).toBeNull();
  });

  it('keeps extracted text documents and Markdown chunks as source text', async () => {
    const textFile = { ...file, path: 'guide.txt', name: 'guide.txt', contentType: 'text/plain' };
    vi.mocked(getKnowledgeDocumentPreview).mockResolvedValue({
      path: textFile.path, name: textFile.name, contentType: 'text/plain', content: '# 原始文本\n- 保留标记',
    });
    vi.mocked(listKnowledgeDocumentChunks).mockResolvedValue({
      items: [{ index: 1, content: '# 切片标题\n- 切片条目', characterCount: 15 }], total: 1, page: 1, size: 20,
    });
    const { container, currentDocument } = mount('preview', undefined, textFile);
    await vi.waitFor(() => expect(container.querySelector('.text-preview pre')?.textContent).toBe('# 原始文本\n- 保留标记'));
    expect(container.querySelector('.markdown-preview')).toBeNull();
    currentDocument.value = { ...file, name: 'guide.markdown', path: 'guide.markdown' };
    click(container, '切片内容');
    await vi.waitFor(() => expect(container.querySelector('.chunk-card pre')?.textContent).toBe('# 切片标题\n- 切片条目'));
    expect(container.querySelector('.chunk-card h1, .chunk-card li')).toBeNull();
  });
});
