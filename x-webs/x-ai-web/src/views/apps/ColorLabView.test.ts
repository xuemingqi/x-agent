// @vitest-environment jsdom
import { createPinia } from 'pinia';
import { createApp, defineComponent, h, nextTick } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { generateColorTheme } from '@/api/color-theme';
import { useModelSettingStore } from '@/stores/model-setting';
import type { ColorTheme } from '@/types/color-theme';
import { copyTheme, INITIAL_COLOR_THEME } from '@/utils/color-theme';
import ColorLabView from './ColorLabView.vue';

vi.mock('@/api/color-theme', () => ({ generateColorTheme: vi.fn() }));
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), warning: vi.fn() } }));

const cleanups: (() => void)[] = [];
beforeEach(() => vi.clearAllMocks());
afterEach(() => cleanups.splice(0).forEach((cleanup) => cleanup()));

async function settle(): Promise<void> {
  for (let step = 0; step < 5; step++) await nextTick();
}

async function mountView(configured = true) {
  const container = document.createElement('div');
  document.body.append(container);
  const pinia = createPinia();
  const app = createApp(ColorLabView).use(pinia);
  app.component('RouterLink', defineComponent({ setup: (_, { slots }) => () => h('a', slots.default?.()) }));
  const settings = useModelSettingStore(pinia);
  settings.loaded = true;
  settings.models = configured ? [{ id: '42', name: 'My Model', modelName: 'model', modelUrl: 'https://example.com/v1', contextWindowSize: 32768, temperature: null, isDefault: true }] : [];
  app.mount(container);
  await settle();
  const unmount = () => { app.unmount(); container.remove(); };
  cleanups.push(unmount);
  return { container, settings, unmount };
}

function button(container: HTMLElement, selector: string): HTMLButtonElement {
  return container.querySelector<HTMLButtonElement>(selector)!;
}

describe('color lab interactions', () => {
  it('uses the current edited colors as lock constraints and avoids duplicate generation', async () => {
    let resolve!: (theme: ColorTheme) => void;
    vi.mocked(generateColorTheme).mockReturnValue(new Promise((done) => { resolve = done; }));
    const { container } = await mountView();
    const primary = container.querySelector<HTMLInputElement>('#hex-primary')!;
    primary.value = '#123456';
    primary.dispatchEvent(new Event('input', { bubbles: true }));
    button(container, '[aria-label="锁定主题色"]').click();
    await settle();
    button(container, '[data-testid="generate"]').click();
    await settle();
    button(container, '[data-testid="generate"]').click();
    expect(generateColorTheme).toHaveBeenCalledTimes(1);
    expect(generateColorTheme).toHaveBeenCalledWith(expect.objectContaining({
      lockedColors: { primary: '#123456' },
      currentPalette: expect.objectContaining({ primary: '#123456' }), improveReadability: false,
    }), expect.any(AbortSignal));
    const result = copyTheme(INITIAL_COLOR_THEME);
    result.name = '新的森林';
    result.palette.primary = '#123456';
    resolve(result);
    await settle();
    expect(container.textContent).toContain('新的森林');
    expect(primary.value).toBe('#123456');
  });

  it('keeps manual edits when generation fails and sends explicit readability intent on retry', async () => {
    vi.mocked(generateColorTheme).mockRejectedValue(new Error('模型暂时不可用'));
    const { container } = await mountView();
    const input = container.querySelector<HTMLInputElement>('#hex-text')!;
    input.value = '#FFFFFF';
    input.dispatchEvent(new Event('change', { bubbles: true }));
    await settle();
    expect(container.querySelector('.contrast-note')).not.toBeNull();
    button(container, '[data-testid="generate"]').click();
    await settle();
    expect(container.querySelector('[role="alert"]')?.textContent).toContain('当前方案已保留');
    expect(input.value).toBe('#FFFFFF');
    button(container, '[data-testid="improve"]').click();
    await settle();
    expect(generateColorTheme).toHaveBeenLastCalledWith(expect.objectContaining({ improveReadability: true }), expect.any(AbortSignal));
  });

  it('opens model settings without calling the model when credentials are missing', async () => {
    const { container, settings } = await mountView(false);
    button(container, '[data-testid="generate"]').click();
    await settle();
    expect(settings.dialogVisible).toBe(true);
    expect(generateColorTheme).not.toHaveBeenCalled();
  });

  it('keeps the pinned comparison unchanged and aborts a pending request when leaving', async () => {
    vi.mocked(generateColorTheme).mockReturnValue(new Promise(() => {}));
    const { container, unmount } = await mountView();
    button(container, '.preview-heading button').click();
    await settle();
    const primary = container.querySelector<HTMLInputElement>('#hex-primary')!;
    primary.value = '#123456';
    primary.dispatchEvent(new Event('change', { bubbles: true }));
    await settle();
    const previews = container.querySelectorAll<HTMLElement>('.theme-preview');
    expect(previews[0]!.style.getPropertyValue('--color-primary')).toBe('#346650');
    expect(previews[1]!.style.getPropertyValue('--color-primary')).toBe('#123456');
    button(container, '[data-testid="generate"]').click();
    await settle();
    const signal = vi.mocked(generateColorTheme).mock.calls[0]![1]!;
    unmount();
    cleanups.pop();
    expect(signal.aborted).toBe(true);
  });
});
