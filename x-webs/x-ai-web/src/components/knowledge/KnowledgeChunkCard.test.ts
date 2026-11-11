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

async function mountCards() {
  const chunks = ref([
    { index: 1, content: '第一段原文\n保留完整内容', characterCount: 13 },
    { index: 2, content: '第二段原文\n保留完整内容', characterCount: 13 },
  ]);
  const container = document.createElement('div');
  const app = createApp({ render: () => h('div', chunks.value.map((chunk) => h(KnowledgeChunkCard, {
    key: chunk.index, chunk, matched: chunk.index === 1,
  }))) });
  app.component('el-icon', defineComponent({ setup: (_, { slots }) => () => h('span', slots.default?.()) }));
  app.mount(container);
  const unmount = () => app.unmount();
  cleanups.push(unmount);
  await nextTick();
  return { container, chunks, unmount };
}

describe('knowledge chunk folding', () => {
  it('expands overflowing cards independently while retaining the source and match marker', async () => {
    vi.spyOn(HTMLElement.prototype, 'scrollHeight', 'get').mockReturnValue(240);
    const { container } = await mountCards();
    const buttons = container.querySelectorAll<HTMLButtonElement>('.chunk-expand');
    expect(buttons).toHaveLength(2);
    expect([...buttons].map((button) => button.getAttribute('aria-expanded'))).toEqual(['false', 'false']);
    expect(buttons[0]!.getAttribute('aria-controls')).not.toBe(buttons[1]!.getAttribute('aria-controls'));

    buttons[0]!.click();
    await nextTick();
    expect(buttons[0]!.getAttribute('aria-expanded')).toBe('true');
    expect(buttons[1]!.getAttribute('aria-expanded')).toBe('false');
    expect(container.querySelector('.chunk-card.matched pre')?.textContent).toBe('第一段原文\n保留完整内容');

    buttons[0]!.click();
    await nextTick();
    expect(container.querySelector('.chunk-card.expanded')).toBeNull();
  });

  it('only offers expansion after overflow and resets folding when the content changes', async () => {
    const height = vi.spyOn(HTMLElement.prototype, 'scrollHeight', 'get').mockReturnValue(72);
    const { container, chunks, unmount } = await mountCards();
    expect(container.querySelector('.chunk-expand')).toBeNull();

    height.mockReturnValue(240);
    resizeCallbacks.forEach((callback) => callback());
    await nextTick();
    container.querySelector<HTMLButtonElement>('.chunk-expand')!.click();
    await nextTick();
    expect(container.querySelector('.chunk-card.expanded')).not.toBeNull();

    chunks.value[0] = { index: 1, content: '刷新后的原文', characterCount: 7 };
    await nextTick();
    await nextTick();
    expect(container.querySelector('.chunk-card.expanded')).toBeNull();
    expect(container.querySelector('.chunk-card pre')?.textContent).toBe('刷新后的原文');

    height.mockReturnValue(72);
    resizeCallbacks.forEach((callback) => callback());
    await nextTick();
    expect(container.querySelector('.chunk-expand')).toBeNull();
    unmount();
    cleanups.pop();
    expect(disconnect).toHaveBeenCalledTimes(2);
  });
});
