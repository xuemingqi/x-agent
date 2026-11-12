// @vitest-environment jsdom
import { createApp, defineComponent, h, nextTick, ref } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import KnowledgeChunkCard from './KnowledgeChunkCard.vue';

const resizeCallbacks: (() => void)[] = [];
const disconnect = vi.fn();
const cleanups: (() => void)[] = [];

beforeEach(() => {
  resizeCallbacks.length = 0;
  disconnect.mockClear();
  vi.stubGlobal('ResizeObserver', class {
    constructor(callback: () => void) { resizeCallbacks.push(callback); }
    observe() {}
    disconnect = disconnect;
  });
});

afterEach(() => {
  cleanups.splice(0).forEach((cleanup) => cleanup());
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

async function mountCards(initialMatchedIndex?: number, contents = ['第一段原文\n保留完整内容', '第二段原文\n保留完整内容']) {
  const matchedIndex = ref(initialMatchedIndex);
  const chunks = ref(contents.map((content, index) => ({ index: index + 1, content, characterCount: content.length })));
  const container = document.createElement('div');
  const app = createApp({ render: () => h('div', chunks.value.map((chunk) => h(KnowledgeChunkCard, {
    key: chunk.index, chunk, matched: chunk.index === matchedIndex.value,
  }))) });
  app.component('el-icon', defineComponent({ setup: (_, { slots }) => () => h('span', slots.default?.()) }));
  app.mount(container);
  const unmount = () => app.unmount();
  cleanups.push(unmount);
  await nextTick();
  return { container, chunks, matchedIndex, unmount };
}

describe('knowledge chunk single-line rows', () => {
  it('folds every row initially, including a hit, and expands full source independently', async () => {
    const { container } = await mountCards(1);
    const rows = container.querySelectorAll<HTMLButtonElement>('button.chunk-row');
    expect(rows).toHaveLength(2);
    expect([...rows].map((row) => row.getAttribute('aria-expanded'))).toEqual(['false', 'false']);
    expect(rows[0]!.getAttribute('aria-controls')).not.toBe(rows[1]!.getAttribute('aria-controls'));
    expect(container.querySelector('.chunk-preview')?.textContent).toBe('第一段原文 保留完整内容');
    expect(container.querySelector('.chunk-card.matched pre')?.textContent).toBe('第一段原文\n保留完整内容');
    expect(container.querySelector<HTMLElement>('.chunk-content')?.style.display).toBe('none');

    rows[0]!.click();
    await nextTick();
    expect(rows[0]!.getAttribute('aria-expanded')).toBe('true');
    expect(rows[1]!.getAttribute('aria-expanded')).toBe('false');
    expect(container.querySelector<HTMLElement>('.chunk-card.expanded .chunk-content')?.style.display).not.toBe('none');
    rows[1]!.click();
    await nextTick();
    rows[0]!.click();
    await nextTick();
    expect(rows[0]!.getAttribute('aria-expanded')).toBe('false');
    expect(rows[1]!.getAttribute('aria-expanded')).toBe('true');
  });

  it('highlights a newly matched row without automatically expanding it', async () => {
    const { container, matchedIndex } = await mountCards();
    matchedIndex.value = 2;
    await nextTick();
    await nextTick();
    expect(container.querySelector('.chunk-card.matched.expanded')).toBeNull();
    expect(container.querySelector('.chunk-card.matched button')?.getAttribute('aria-expanded')).toBe('false');
    expect(container.querySelector('.chunk-card.matched pre')?.textContent).toBe('第二段原文\n保留完整内容');
  });

  it('only offers expansion for overflowing or multiline text and remeasures on resize', async () => {
    const width = vi.spyOn(HTMLElement.prototype, 'scrollWidth', 'get').mockReturnValue(100);
    vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(300);
    const { container, chunks, unmount } = await mountCards(undefined, ['简短文本', '另一段短文']);
    expect(container.querySelector('button.chunk-row')).toBeNull();
    expect(container.querySelector('.chunk-toggle')).toBeNull();

    width.mockReturnValue(500);
    resizeCallbacks.forEach((callback) => callback());
    await nextTick();
    const row = container.querySelector<HTMLButtonElement>('button.chunk-row')!;
    expect(row).not.toBeNull();
    row.click();
    await nextTick();
    expect(row.getAttribute('aria-expanded')).toBe('true');
    chunks.value[0] = { index: 1, content: '刷新后的原文', characterCount: 7 };
    await nextTick();
    await nextTick();
    expect(container.querySelector('.chunk-card.expanded')).toBeNull();
    expect(container.querySelector('.chunk-card pre')?.textContent).toBe('刷新后的原文');

    width.mockReturnValue(100);
    resizeCallbacks.forEach((callback) => callback());
    await nextTick();
    expect(container.querySelector('button.chunk-row')).toBeNull();
    unmount();
    cleanups.pop();
    expect(disconnect).toHaveBeenCalled();
  });

  it('keeps markup as source text in the one-line preview and expanded content', async () => {
    const text = '<script>stealToken()</script>\n<img src=x onerror=stealToken()>';
    const { container } = await mountCards(undefined, [text]);
    expect(container.querySelector('.chunk-preview')?.textContent).toContain('<script>');
    container.querySelector<HTMLButtonElement>('button.chunk-row')!.click();
    await nextTick();
    expect(container.querySelector('.chunk-content pre')?.textContent).toBe(text);
    expect(container.querySelector('script, img, [onerror]')).toBeNull();
  });
});
