// @vitest-environment jsdom
import { createPinia } from 'pinia';
import { ElMessage } from 'element-plus';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createApp, defineComponent, h, nextTick } from 'vue';

import {
  createKnowledgeBase, deleteKnowledgeBase, listKnowledgeBases, listKnowledgeDocuments, reindexKnowledgeDocument, updateKnowledgeBase,
  searchKnowledgeDocuments, uploadKnowledgeDocuments,
} from '@/api/knowledge';
import type { KnowledgeDocument } from '@/types/knowledge';
import KnowledgeView from './KnowledgeView.vue';

const { prompt, confirm } = vi.hoisted(() => ({ prompt: vi.fn(), confirm: vi.fn() }));

vi.mock('@/api/knowledge', () => ({
  listKnowledgeBases: vi.fn(), createKnowledgeBase: vi.fn(), updateKnowledgeBase: vi.fn(), deleteKnowledgeBase: vi.fn(),
  listKnowledgeDocuments: vi.fn(),
  uploadKnowledgeDocuments: vi.fn(),
  deleteKnowledgeDocument: vi.fn(),
  downloadKnowledgeDocument: vi.fn(),
  reindexKnowledgeDocument: vi.fn(), searchKnowledgeDocuments: vi.fn(),
}));
vi.mock('@/components/knowledge/KnowledgeLinkPanel.vue', () => ({ default: {
  template: '<section class="knowledge-link-panel-stub"><input aria-label="在线链接筛选" /></section>',
} }));
vi.mock('@/components/knowledge/KnowledgeDocumentReader.vue', () => ({
  default: {
    props: ['document', 'initialTab', 'initialChunkIndex'],
    emits: ['close', 'reindex'],
    template: `<section class="document-reader" :data-tab="initialTab" :data-chunk="initialChunkIndex">
      <button title="返回文档列表" @click="$emit('close')">文档列表</button>
      <h2>{{ document.name }}</h2>
      <button title="重新索引" @click="$emit('reindex')">重新索引</button>
    </section>`,
  },
}));
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm, prompt },
}));

const cleanups: (() => void)[] = [];
const commonBase = { id: 'common', name: '公共知识库', description: '', isDefault: true };
const projectBase = { id: 'project', name: '项目知识库', description: '', isDefault: false };
const document: KnowledgeDocument = {
  knowledgeBaseId: 'common', knowledgeBaseName: '公共知识库',
  path: 'guide.txt', name: 'guide.txt', size: 100, contentType: 'text/plain', updateTime: '',
  status: 'READY', chunkCount: 2, embeddingModel: 'test-embedding', errorMessage: null,
};

beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(listKnowledgeBases).mockResolvedValue([commonBase, projectBase]);
  vi.mocked(listKnowledgeDocuments).mockResolvedValue([document]);
});
afterEach(() => cleanups.splice(0).forEach((cleanup) => cleanup()));

async function mount() {
  const container = window.document.createElement('div');
  const app = createApp(KnowledgeView).use(createPinia());
  const wrapper = defineComponent({ setup: (_, { slots }) => () => h('div', slots.default?.()) });
  for (const name of ['el-icon', 'el-empty']) app.component(name, wrapper);
  app.component('el-button', defineComponent({
    props: ['loading', 'disabled', 'nativeType'],
    setup: (props, { slots }) => () => h('button', {
      disabled: props.loading || props.disabled, type: props.nativeType || 'button',
    }, slots.default?.()),
  }));
  app.component('el-input', defineComponent({
    props: ['modelValue'], emits: ['update:modelValue'],
    setup: (props, { emit }) => () => h('input', {
      value: props.modelValue, onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLInputElement).value),
    }),
  }));
  app.directive('loading', {});
  app.mount(container);
  cleanups.push(() => app.unmount());
  for (let step = 0; step < 6; step++) await nextTick();
  return container;
}

function knowledgeBaseButton(container: HTMLElement, id: string): HTMLButtonElement {
  const button = container.querySelector<HTMLButtonElement>(`.knowledge-base-item[data-knowledge-base-id="${id}"]`);
  expect(button).not.toBeNull();
  return button!;
}

describe('RAG knowledge management', () => {
  it('shows every knowledge base directly and explains automatic common retrieval', async () => {
    const container = await mount();
    expect(listKnowledgeDocuments).toHaveBeenCalledWith('common');
    expect([...container.querySelectorAll('.knowledge-base-item')].map((button) => button.getAttribute('aria-label')))
      .toEqual(['公共知识库', '项目知识库']);
    expect(container.querySelector('select, [role="combobox"]')).toBeNull();
    expect(knowledgeBaseButton(container, 'common').getAttribute('aria-pressed')).toBe('true');
    expect(knowledgeBaseButton(container, 'project').getAttribute('aria-pressed')).toBe('false');
    expect(container.textContent).toContain('所有会话自动检索 · 仅自己可见');
    expect([...container.querySelectorAll('button')].some((button) => button.textContent?.includes('删除知识库'))).toBe(false);
    expect(container.querySelector('.document-summary > strong')?.textContent).toBe('guide.txt');
  });

  it('pins the common knowledge base first even when the catalog arrives in another order', async () => {
    vi.mocked(listKnowledgeBases).mockResolvedValue([
      projectBase, { ...projectBase, id: 'engineering', name: '工程规范' }, commonBase,
    ]);

    const container = await mount();

    expect([...container.querySelectorAll('.knowledge-base-item')].map((button) => button.getAttribute('aria-label')))
      .toEqual(['公共知识库', '项目知识库', '工程规范']);
    expect(knowledgeBaseButton(container, 'common').getAttribute('aria-pressed')).toBe('true');
    expect(listKnowledgeDocuments).toHaveBeenCalledWith('common');
  });

  it('discards an obsolete search after the query is cleared', async () => {
    let finish!: (value: Awaited<ReturnType<typeof searchKnowledgeDocuments>>) => void;
    vi.mocked(searchKnowledgeDocuments).mockReturnValueOnce(new Promise((resolve) => { finish = resolve; }));
    const container = await mount();
    const query = container.querySelector<HTMLInputElement>('.search-form input')!;
    query.value = '订单退款';
    query.dispatchEvent(new Event('input', { bubbles: true }));
    await nextTick();
    container.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    expect(searchKnowledgeDocuments).toHaveBeenCalledWith('common', '订单退款');
    await nextTick();
    container.querySelector<HTMLButtonElement>('.catalog-text-action')!.click();
    finish([{ knowledgeBaseId: 'common', knowledgeBaseName: '公共知识库', path: document.path, name: 'obsolete-result.txt', chunkIndex: 0, content: '文档', score: 0.9 }]);
    await nextTick();
    await nextTick();
    expect(container.textContent).not.toContain('obsolete-result.txt');
    expect(container.querySelector('.search-results')).toBeNull();
  });

  it('preserves failed uploads for retry and refreshes their successful indexing state', async () => {
    const failed = { ...document, status: 'FAILED' as const, chunkCount: 0, errorMessage: 'Ollama 连接失败' };
    vi.mocked(uploadKnowledgeDocuments).mockResolvedValue([failed]);
    const container = await mount();
    vi.mocked(listKnowledgeDocuments).mockResolvedValue([failed]);
    const input = container.querySelector<HTMLInputElement>('input[type="file"]')!;
    Object.defineProperty(input, 'files', { value: [new File(['知识'], 'guide.txt')] });
    input.dispatchEvent(new Event('change', { bubbles: true }));
    await vi.waitFor(() => expect(container.querySelector('.index-status')?.textContent).toBe('索引失败'));
    expect(ElMessage.warning).toHaveBeenCalledWith('已保存 1 个文档，1 个索引失败，可重新索引');
    expect(container.textContent).toContain('Ollama 连接失败');
    vi.mocked(reindexKnowledgeDocument).mockResolvedValue(document);
    vi.mocked(listKnowledgeDocuments).mockResolvedValue([document]);
    container.querySelector<HTMLButtonElement>('.document-name')!.click();
    await nextTick();
    container.querySelector<HTMLButtonElement>('[title="重新索引"]')!.click();
    await vi.waitFor(() => expect(container.querySelector('.index-status')?.textContent).toBe('已索引'));
    expect(reindexKnowledgeDocument).toHaveBeenCalledWith('common', 'guide.txt');
    expect(container.textContent).not.toContain('Ollama 连接失败');
  });

  it('allows retrying an indexing document left unfinished by a stopped server', async () => {
    vi.mocked(listKnowledgeDocuments).mockResolvedValue([{ ...document, status: 'INDEXING' }]);
    vi.mocked(reindexKnowledgeDocument).mockResolvedValue(document);
    const container = await mount();
    container.querySelector<HTMLButtonElement>('.document-name')!.click();
    await nextTick();
    const retry = container.querySelector<HTMLButtonElement>('[title="重新索引"]')!;
    expect(retry.disabled).toBe(false);
    vi.mocked(listKnowledgeDocuments).mockResolvedValue([document]);
    retry.click();
    await vi.waitFor(() => expect(container.querySelector('.index-status')?.textContent).toBe('已索引'));
    expect(reindexKnowledgeDocument).toHaveBeenCalledWith('common', 'guide.txt');
  });

  it('opens a focused reader in the page and restores the same catalog on return', async () => {
    const container = await mount();
    const catalog = container.querySelector('.document-list');
    expect(container.querySelector('.knowledge-page.is-reading')).toBeNull();
    expect(container.querySelector('.knowledge-empty-reader')).not.toBeNull();
    container.querySelector<HTMLButtonElement>('.document-name')!.click();
    await nextTick();
    expect(container.querySelector('.knowledge-page.is-reading')).not.toBeNull();
    expect(container.querySelector('.knowledge-workspace.has-document .document-reader h2')?.textContent).toBe('guide.txt');
    expect(container.querySelector('.el-drawer, .el-overlay, el-drawer')).toBeNull();
    expect(container.querySelector('.document-list')).toBe(catalog);
    container.querySelector<HTMLButtonElement>('[title="返回文档列表"]')!.click();
    await nextTick();
    expect(container.querySelector('.knowledge-page.is-reading')).toBeNull();
    expect(container.querySelector('.document-reader')).toBeNull();
    expect(container.querySelector('.document-list')).toBe(catalog);
    expect(knowledgeBaseButton(container, 'common').getAttribute('aria-pressed')).toBe('true');
    expect(listKnowledgeDocuments).toHaveBeenCalledTimes(1);
    expect(container.querySelector('.knowledge-empty-reader')).not.toBeNull();
  });

  it('opens a semantic hit in the reader on its matching chunk page', async () => {
    vi.mocked(searchKnowledgeDocuments).mockResolvedValue([{
      knowledgeBaseId: 'common', knowledgeBaseName: '公共知识库',
      path: document.path, name: document.name, chunkIndex: 23, content: '退款需要提供订单号', score: 0.9,
    }]);
    const container = await mount();
    const query = container.querySelector<HTMLInputElement>('.search-form input')!;
    query.value = '退款规则';
    query.dispatchEvent(new Event('input', { bubbles: true }));
    await nextTick();
    container.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await vi.waitFor(() => expect(container.querySelector('.search-result')).not.toBeNull());
    container.querySelector<HTMLButtonElement>('.search-result')!.click();
    await nextTick();
    expect(container.querySelector('.knowledge-page.is-reading')).not.toBeNull();
    expect(container.querySelector('.document-reader')?.getAttribute('data-tab')).toBe('chunks');
    expect(container.querySelector('.document-reader')?.getAttribute('data-chunk')).toBe('23');
    expect(container.querySelector('.search-result.selected')).not.toBeNull();
  });

  it('returns from a matched chunk to its search results and clears that context only after changing knowledge bases', async () => {
    vi.mocked(searchKnowledgeDocuments).mockResolvedValue([{
      knowledgeBaseId: 'common', knowledgeBaseName: '公共知识库',
      path: document.path, name: document.name, chunkIndex: 23, content: '退款需要提供订单号', score: 0.9,
    }]);
    const container = await mount();
    const query = container.querySelector<HTMLInputElement>('.search-form input')!;
    query.value = '退款规则';
    query.dispatchEvent(new Event('input', { bubbles: true }));
    await nextTick();
    container.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await vi.waitFor(() => expect(container.querySelector('.search-result')).not.toBeNull());
    const result = container.querySelector<HTMLButtonElement>('.search-result')!;
    result.click();
    await nextTick();
    expect(container.querySelector('.knowledge-page.is-reading')).not.toBeNull();

    container.querySelector<HTMLButtonElement>('[title="返回文档列表"]')!.click();
    await nextTick();

    expect(container.querySelector('.knowledge-page.is-reading')).toBeNull();
    expect(container.querySelector('.document-reader')).toBeNull();
    expect(container.querySelector('.search-result')).toBe(result);
    expect(query.value).toBe('退款规则');
    expect(container.querySelector('.searched-query')?.textContent).toContain('退款规则');
    expect(searchKnowledgeDocuments).toHaveBeenCalledTimes(1);
    expect(listKnowledgeDocuments).toHaveBeenCalledTimes(1);

    vi.mocked(listKnowledgeDocuments).mockResolvedValue([]);
    knowledgeBaseButton(container, 'project').click();
    await nextTick();

    expect(query.value).toBe('');
    expect(container.querySelector('.search-results')).toBeNull();
    expect(knowledgeBaseButton(container, 'project').getAttribute('aria-pressed')).toBe('true');
    expect(listKnowledgeDocuments).toHaveBeenLastCalledWith('project');
  });

  it('clears document and search selections immediately when switching knowledge bases', async () => {
    let finish!: (value: Awaited<ReturnType<typeof searchKnowledgeDocuments>>) => void;
    vi.mocked(searchKnowledgeDocuments).mockReturnValueOnce(new Promise((resolve) => { finish = resolve; }));
    const container = await mount();
    container.querySelector<HTMLButtonElement>('.document-name')!.click();
    const query = container.querySelector<HTMLInputElement>('.search-form input')!;
    query.value = '订单退款';
    query.dispatchEvent(new Event('input', { bubbles: true }));
    await nextTick();
    container.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    vi.mocked(listKnowledgeDocuments).mockResolvedValue([]);
    knowledgeBaseButton(container, 'project').click();
    await nextTick();
    expect(listKnowledgeDocuments).toHaveBeenLastCalledWith('project');
    expect(knowledgeBaseButton(container, 'project').getAttribute('aria-pressed')).toBe('true');
    expect(knowledgeBaseButton(container, 'common').getAttribute('aria-pressed')).toBe('false');
    expect(container.querySelector('.document-reader')).toBeNull();
    finish([{ ...document, name: '过期资料', chunkIndex: 1, content: '旧检索结果', score: 0.9 }]);
    await nextTick();
    await nextTick();
    expect(container.textContent).not.toContain('过期资料');
    expect(container.querySelector('.search-results')).toBeNull();
    expect(container.querySelector('.document-summary')).toBeNull();
  });

  it('discards a delayed catalog response from a previous knowledge base', async () => {
    let finish!: (value: KnowledgeDocument[]) => void;
    vi.mocked(listKnowledgeDocuments).mockReturnValueOnce(new Promise((resolve) => { finish = resolve; }));
    const container = await mount();
    vi.mocked(listKnowledgeDocuments).mockResolvedValue([{ ...document, knowledgeBaseId: 'project', name: 'project.txt' }]);
    knowledgeBaseButton(container, 'project').click();
    await vi.waitFor(() => expect(container.querySelector('.document-summary > strong')?.textContent).toBe('project.txt'));
    finish([document]);
    await nextTick();
    await nextTick();
    expect(container.querySelector('.document-summary > strong')?.textContent).toBe('project.txt');
  });

  it('creates a knowledge base and selects it for subsequent uploads', async () => {
    prompt.mockResolvedValue({ value: '新知识库', action: 'confirm' });
    vi.mocked(createKnowledgeBase).mockResolvedValue({ ...projectBase, id: 'new', name: '新知识库' });
    const container = await mount();
    vi.mocked(listKnowledgeBases).mockResolvedValue([commonBase, projectBase, { ...projectBase, id: 'new', name: '新知识库' }]);
    [...container.querySelectorAll('button')].find((button) => button.textContent?.includes('创建知识库'))!.click();
    await vi.waitFor(() => expect(knowledgeBaseButton(container, 'new').getAttribute('aria-pressed')).toBe('true'));
    expect(createKnowledgeBase).toHaveBeenCalledWith({ name: '新知识库', description: '' });
    vi.mocked(uploadKnowledgeDocuments).mockResolvedValue([]);
    const input = container.querySelector<HTMLInputElement>('input[type="file"]')!;
    const file = new File(['资料'], 'new.txt');
    Object.defineProperty(input, 'files', { value: [file] });
    input.dispatchEvent(new Event('change', { bubbles: true }));
    await vi.waitFor(() => expect(uploadKnowledgeDocuments).toHaveBeenCalledWith('new', [file]));
  });

  it('renames common knowledge without exposing a delete action and deletes other bases', async () => {
    prompt.mockResolvedValue({ value: '团队资料', action: 'confirm' });
    vi.mocked(updateKnowledgeBase).mockResolvedValue({ ...commonBase, name: '团队资料' });
    const container = await mount();
    [...container.querySelectorAll('button')].find((button) => button.textContent?.trim() === '重命名')!.click();
    await vi.waitFor(() => expect(updateKnowledgeBase).toHaveBeenCalledWith('common', { name: '团队资料', description: '' }));
    await vi.waitFor(() => expect(knowledgeBaseButton(container, 'project').disabled).toBe(false));
    knowledgeBaseButton(container, 'project').click();
    await nextTick();
    confirm.mockResolvedValue('confirm');
    vi.mocked(deleteKnowledgeBase).mockResolvedValue(true);
    vi.mocked(listKnowledgeBases).mockResolvedValue([commonBase]);
    [...container.querySelectorAll('button')].find((button) => button.textContent?.trim() === '删除知识库')!.click();
    await vi.waitFor(() => expect(deleteKnowledgeBase).toHaveBeenCalledWith('project'));
    await vi.waitFor(() => expect(knowledgeBaseButton(container, 'common').getAttribute('aria-pressed')).toBe('true'));
    expect(container.querySelector('.knowledge-base-item[data-knowledge-base-id="project"]')).toBeNull();
  });

  it('keeps the current document and semantic search when its active knowledge base is clicked again', async () => {
    vi.mocked(searchKnowledgeDocuments).mockResolvedValue([{
      knowledgeBaseId: 'common', knowledgeBaseName: '公共知识库',
      path: document.path, name: document.name, chunkIndex: 3, content: '退款需要提供订单号', score: 0.9,
    }]);
    const container = await mount();
    const query = container.querySelector<HTMLInputElement>('.search-form input')!;
    query.value = '退款规则';
    query.dispatchEvent(new Event('input', { bubbles: true }));
    await nextTick();
    container.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await vi.waitFor(() => expect(container.querySelector('.search-result')).not.toBeNull());
    container.querySelector<HTMLButtonElement>('.search-result')!.click();
    await nextTick();

    knowledgeBaseButton(container, 'common').click();
    await nextTick();

    expect(listKnowledgeDocuments).toHaveBeenCalledTimes(1);
    expect(searchKnowledgeDocuments).toHaveBeenCalledTimes(1);
    expect(query.value).toBe('退款规则');
    expect(container.querySelector('.search-result.selected')).not.toBeNull();
    expect(container.querySelector('.document-reader')?.getAttribute('data-tab')).toBe('chunks');
    expect(container.querySelector('.document-reader')?.getAttribute('data-chunk')).toBe('3');
  });

  it('uploads new files into the knowledge base selected from the visible list', async () => {
    const container = await mount();
    vi.mocked(listKnowledgeDocuments).mockResolvedValue([]);
    knowledgeBaseButton(container, 'project').click();
    await nextTick();
    vi.mocked(uploadKnowledgeDocuments).mockResolvedValue([]);
    const input = container.querySelector<HTMLInputElement>('input[type="file"]')!;
    const file = new File(['项目资料'], 'project.txt');
    Object.defineProperty(input, 'files', { value: [file] });

    input.dispatchEvent(new Event('change', { bubbles: true }));

    await vi.waitFor(() => expect(uploadKnowledgeDocuments).toHaveBeenCalledWith('project', [file]));
    expect(uploadKnowledgeDocuments).not.toHaveBeenCalledWith('common', expect.anything());
  });

  it('keeps the independent online links view mounted while switching document knowledge bases', async () => {
    const container = await mount();
    const linksPanel = container.querySelector<HTMLElement>('.knowledge-link-panel-stub')!;
    const linksRegion = container.querySelector<HTMLElement>('[aria-label="个人在线链接"]')!;
    const filter = linksPanel.querySelector<HTMLInputElement>('input')!;
    filter.value = '项目在线文档';

    [...container.querySelectorAll('button')].find((button) => button.textContent?.includes('在线链接'))!.click();
    await nextTick();
    expect(linksRegion.style.display).not.toBe('none');
    knowledgeBaseButton(container, 'project').click();
    await nextTick();

    expect(container.querySelector('.knowledge-link-panel-stub')).toBe(linksPanel);
    expect(filter.value).toBe('项目在线文档');
    expect(linksRegion.style.display).toBe('none');
    expect(listKnowledgeDocuments).toHaveBeenLastCalledWith('project');
  });

  it('does not request unscoped documents when the knowledge base catalog fails', async () => {
    vi.mocked(listKnowledgeBases).mockRejectedValue(new Error('知识库不可用'));
    const container = await mount();
    expect(listKnowledgeDocuments).not.toHaveBeenCalled();
    expect(container.querySelector('[role="alert"]')?.textContent).toContain('知识库不可用');
    const uploadAction = container.querySelector<HTMLButtonElement>('.page-primary-action');
    expect(!uploadAction || uploadAction.disabled).toBe(true);
  });

  it('offers an inline retry when the selected knowledge base documents cannot be loaded', async () => {
    vi.mocked(listKnowledgeDocuments).mockRejectedValueOnce(new Error('文档暂时不可用'));
    const container = await mount();
    expect(container.querySelector('.catalog-load-error[role="alert"]')?.textContent).toContain('文档暂时不可用');
    vi.mocked(listKnowledgeDocuments).mockResolvedValue([document]);

    container.querySelector<HTMLButtonElement>('.catalog-load-error button')!.click();

    await vi.waitFor(() => expect(container.querySelector('.document-summary > strong')?.textContent).toBe('guide.txt'));
    expect(listKnowledgeDocuments).toHaveBeenLastCalledWith('common');
    expect(container.querySelector('.catalog-load-error')).toBeNull();
  });

});
