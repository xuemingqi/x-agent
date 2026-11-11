// @vitest-environment jsdom
import { createPinia } from 'pinia';
import { ElMessage } from 'element-plus';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createApp, defineComponent, h, nextTick } from 'vue';

import {
  listKnowledgeDocuments, listPublicKnowledgeDocuments, reindexKnowledgeDocument,
  searchKnowledgeDocuments, uploadKnowledgeDocuments,
} from '@/api/knowledge';
import type { KnowledgeDocument } from '@/types/knowledge';
import KnowledgeView from './KnowledgeView.vue';

vi.mock('@/api/knowledge', () => ({
  listKnowledgeDocuments: vi.fn(), listPublicKnowledgeDocuments: vi.fn(),
  uploadKnowledgeDocuments: vi.fn(), uploadPublicKnowledgeDocuments: vi.fn(),
  deleteKnowledgeDocument: vi.fn(), deletePublicKnowledgeDocument: vi.fn(),
  downloadKnowledgeDocument: vi.fn(), downloadPublicKnowledgeDocument: vi.fn(),
  reindexKnowledgeDocument: vi.fn(), searchKnowledgeDocuments: vi.fn(),
}));
vi.mock('@/api/administration', () => ({
  getAdministrationStatus: vi.fn().mockResolvedValue({ administrator: true, role: 'ADMIN' }),
}));
vi.mock('@/components/knowledge/KnowledgeLinkPanel.vue', () => ({ default: { template: '<div />' } }));
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
  ElMessageBox: { confirm: vi.fn() },
}));

const cleanups: (() => void)[] = [];
const document: KnowledgeDocument = {
  path: 'guide.txt', name: 'guide.txt', size: 100, contentType: 'text/plain', updateTime: '',
  status: 'READY', chunkCount: 2, embeddingModel: 'test-embedding', errorMessage: null,
};

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(listKnowledgeDocuments).mockResolvedValue([document]);
  vi.mocked(listPublicKnowledgeDocuments).mockResolvedValue([{ ...document, name: 'public.txt', path: 'public.txt' }]);
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

function switchToPublic(container: HTMLElement): void {
  [...container.querySelectorAll('.scope-switch button')]
    .find((button) => button.textContent?.includes('公共知识库'))!
    .dispatchEvent(new MouseEvent('click', { bubbles: true }));
}

describe('RAG knowledge management', () => {
  it('keeps public files visible when an older private listing finishes later', async () => {
    let finish!: (value: KnowledgeDocument[]) => void;
    vi.mocked(listKnowledgeDocuments).mockReturnValueOnce(new Promise((resolve) => { finish = resolve; }));
    const container = await mount();
    switchToPublic(container);
    await vi.waitFor(() => expect(container.querySelector('.document-summary > strong')?.textContent).toBe('public.txt'));
    finish([document]);
    await nextTick();
    await nextTick();
    expect(container.querySelector('.document-summary > strong')?.textContent).toBe('public.txt');
  });

  it('discards semantic search results after switching knowledge scopes', async () => {
    let finish!: (value: Awaited<ReturnType<typeof searchKnowledgeDocuments>>) => void;
    vi.mocked(searchKnowledgeDocuments).mockReturnValueOnce(new Promise((resolve) => { finish = resolve; }));
    const container = await mount();
    const query = container.querySelector<HTMLInputElement>('.search-form input')!;
    query.value = '订单退款';
    query.dispatchEvent(new Event('input', { bubbles: true }));
    await nextTick();
    container.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    expect(searchKnowledgeDocuments).toHaveBeenCalledWith('订单退款', 'user');
    switchToPublic(container);
    finish([{ path: document.path, name: 'private-result.txt', chunkIndex: 0, content: '个人文档', score: 0.9, scope: 'user' }]);
    await nextTick();
    await nextTick();
    expect(container.textContent).not.toContain('private-result.txt');
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
    expect(reindexKnowledgeDocument).toHaveBeenCalledWith('guide.txt', 'user');
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
    expect(reindexKnowledgeDocument).toHaveBeenCalledWith('guide.txt', 'user');
  });

  it('keeps document reading in the page and returns to the catalog without an overlay', async () => {
    const container = await mount();
    expect(container.querySelector('.knowledge-empty-reader')).not.toBeNull();
    container.querySelector<HTMLButtonElement>('.document-name')!.click();
    await nextTick();
    expect(container.querySelector('.knowledge-workspace.has-document .document-reader h2')?.textContent).toBe('guide.txt');
    expect(container.querySelector('.el-drawer, .el-overlay, el-drawer')).toBeNull();
    expect(container.querySelector('.document-list')).not.toBeNull();
    container.querySelector<HTMLButtonElement>('[title="返回文档列表"]')!.click();
    await nextTick();
    expect(container.querySelector('.document-reader')).toBeNull();
    expect(container.querySelector('.knowledge-empty-reader')).not.toBeNull();
  });

  it('opens a semantic hit in the reader on its matching chunk page', async () => {
    vi.mocked(searchKnowledgeDocuments).mockResolvedValue([{
      path: document.path, name: document.name, chunkIndex: 23, content: '退款需要提供订单号', score: 0.9, scope: 'user',
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
    expect(container.querySelector('.document-reader')?.getAttribute('data-tab')).toBe('chunks');
    expect(container.querySelector('.document-reader')?.getAttribute('data-chunk')).toBe('23');
    expect(container.querySelector('.search-result.selected')).not.toBeNull();
  });
});
