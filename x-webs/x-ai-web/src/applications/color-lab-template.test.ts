/// <reference types="node" />
// @vitest-environment jsdom
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import ts from 'typescript';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const directory = resolve(process.cwd(), '../../x-services/x-ai/src/main/resources/agent-app-templates/color-lab');
const source = readFileSync(resolve(directory, 'app.ts'), 'utf8');
const html = readFileSync(resolve(directory, 'index.html'), 'utf8');
const manifestJson = readFileSync(resolve(directory, 'manifest.json'), 'utf8');
const javascript = ts.transpileModule(`async function startColorLab() {\n${source}\n}`, {
  fileName: 'color-lab.ts', compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
}).outputText;
const run = new Function('app', `${javascript}\nreturn startColorLab();`) as (app: unknown) => Promise<void>;
const palette = { background: '#F2F4EF', surface: '#FFFFFF', primary: '#264E38', onPrimary: '#FFFFFF',
  text: '#172B20', mutedText: '#4E6256', border: '#BDCFBF' };
const theme = () => ({ name: '新的森林', description: '温和而清晰的森林色调', palette: { ...palette } });
const generate = vi.fn<(request: { prompt: string; systemPrompt: string }) => Promise<{ text: string }>>();
const get = vi.fn<(key: string) => Promise<unknown>>();
const set = vi.fn<(key: string, value: unknown) => Promise<boolean>>();

beforeEach(() => {
  vi.resetAllMocks();
  document.body.innerHTML = html;
  get.mockResolvedValue(null);
  set.mockResolvedValue(true);
  generate.mockResolvedValue({ text: JSON.stringify(theme()) });
});

afterEach(() => {
  window.dispatchEvent(new Event('pagehide'));
  document.body.replaceChildren();
});

describe('TypeScript color lab template', () => {
  it('is a self-contained template and calculates contrast locally from edited HEX colors', async () => {
    const manifest = JSON.parse(manifestJson);
    expect(manifest.permissions).toEqual(['model.generate', 'storage']);
    expect(source).not.toMatch(/\b(?:import|export)\s/);
    await mount();

    updateHex('text', '#000000');
    updateHex('surface', '#FFFFFF');
    expect(contrastCheck('正文 / 卡片')).toContain('21.00:1');
    updateHex('text', '#777777');
    updateHex('surface', '#777777');
    expect(contrastCheck('正文 / 卡片')).toContain('1.00:1 · 待调整');
    expect(generate).not.toHaveBeenCalled();
    expect(document.querySelectorAll('.preview-cards input')).toHaveLength(2);
    const save = document.querySelector<HTMLButtonElement>('#current-preview [data-preview-save]')!;
    save.click();
    expect(save.getAttribute('aria-pressed')).toBe('true');
  });

  it('passes complete generation rules through the generic SDK and preserves locks even when the model ignores them', async () => {
    await mount();
    updateHex('primary', '#123456');
    button('[aria-label="锁定主题色"]').click();
    button('#generate').click();

    await vi.waitFor(() => expect(element('#theme-name').textContent).toBe('新的森林'));
    const request = generate.mock.calls[0]![0];
    expect(request.systemPrompt).toContain('只输出一个 JSON 对象');
    expect(request.systemPrompt).toContain('background');
    expect(JSON.parse(request.prompt)).toMatchObject({ lockedColors: { primary: '#123456' },
      currentPalette: { primary: '#123456' }, improveReadability: false });
    expect(input('#hex-primary').value).toBe('#123456');
    expect(document.querySelector<HTMLElement>('#current-preview')!.style.getPropertyValue('--color-primary')).toBe('#123456');
    await vi.waitFor(() => expect(set).toHaveBeenCalledWith('color-lab.state', expect.objectContaining({
      current: expect.objectContaining({ name: '新的森林', palette: expect.objectContaining({ primary: '#123456' }) }),
      history: [expect.objectContaining({ name: '新的森林' })], locked: ['primary'],
    })));
  });

  it('keeps manual edits when model JSON or a color is invalid and makes readability an explicit request', async () => {
    await mount();
    updateHex('text', '#FFFFFF');
    generate.mockResolvedValueOnce({ text: JSON.stringify({ ...theme(), palette: { ...palette, primary: 'red; background: url(x)' } }) });
    button('#generate').click();
    await vi.waitFor(() => expect(element('#error').hidden).toBe(false));
    expect(element('#error').textContent).toContain('当前方案已保留');
    expect(input('#hex-text').value).toBe('#FFFFFF');
    expect(element('#theme-name').textContent).toBe('雨后森林');
    expect(document.querySelectorAll('#history-list button')).toHaveLength(0);

    button('#improve-readability').click();
    await vi.waitFor(() => expect(generate).toHaveBeenCalledTimes(2));
    expect(JSON.parse(generate.mock.calls[1]![0].prompt).improveReadability).toBe(true);
    await vi.waitFor(() => expect(element('#theme-source').textContent).toBe('AI 优化'));
  });

  it('pins an independent comparison and restores persistent history without overwriting later manual colors', async () => {
    get.mockResolvedValue({ current: theme(), baseline: null, history: [theme()], locked: ['primary'], prompt: '清晰界面', dark: true });
    await mount();
    expect(input('#inspiration').value).toBe('清晰界面');
    expect(input('#dark-mode').checked).toBe(true);
    expect(button('[aria-label="解锁主题色"]').getAttribute('aria-pressed')).toBe('true');
    button('#pin-current').click();
    updateHex('primary', '#998877');
    expect(document.querySelector<HTMLElement>('#baseline-preview')!.style.getPropertyValue('--color-primary')).toBe('#264E38');
    expect(document.querySelector<HTMLElement>('#current-preview')!.style.getPropertyValue('--color-primary')).toBe('#998877');

    button('#history-list button').click();
    expect(input('#hex-primary').value).toBe('#264E38');
    expect(button('[aria-label="锁定主题色"]').getAttribute('aria-pressed')).toBe('false');
    expect(element('#theme-source').textContent).toBe('历史方案');
  });

  it('locks manual controls while a request runs and avoids duplicate generation', async () => {
    let complete!: (value: { text: string }) => void;
    generate.mockReturnValue(new Promise(resolve => { complete = resolve; }));
    await mount();
    updateHex('primary', '#123456');
    button('#generate').click();
    button('#generate').click();
    expect(generate).toHaveBeenCalledTimes(1);
    expect(document.querySelector<HTMLFieldSetElement>('#palette-controls')!.disabled).toBe(true);
    updateHex('primary', '#999999');
    complete({ text: JSON.stringify(theme()) });
    await vi.waitFor(() => expect(element('#theme-name').textContent).toBe('新的森林'));
    expect(input('#hex-primary').value).toBe('#264E38');
  });

  it('provides current CSS and JSON as selectable text when the iframe cannot write to the clipboard', async () => {
    await mount();
    updateHex('primary', '#123456');
    button('#export-css').click();
    await vi.waitFor(() => expect(element('#notice').textContent).toContain('可手动复制'));
    expect((document.querySelector('#export-panel') as HTMLDetailsElement).open).toBe(true);
    expect(input('#export-content').value).toContain('--color-primary: #123456;');
    expect(input('#export-content').value.match(/--color-/g)).toHaveLength(7);
    button('#export-json').click();
    expect(JSON.parse(input('#export-content').value).palette.primary).toBe('#123456');
  });
});

async function mount(): Promise<void> {
  await run({ ai: { generate }, storage: { get, set } });
}

function element(selector: string): HTMLElement {
  return document.querySelector<HTMLElement>(selector)!;
}

function input(selector: string): HTMLInputElement {
  return document.querySelector<HTMLInputElement>(selector)!;
}

function button(selector: string): HTMLButtonElement {
  return document.querySelector<HTMLButtonElement>(selector)!;
}

function updateHex(role: string, value: string): void {
  const field = input(`#hex-${role}`);
  field.value = value;
  field.dispatchEvent(new Event('input', { bubbles: true }));
  field.dispatchEvent(new Event('change', { bubbles: true }));
}

function contrastCheck(label: string): string {
  return [...document.querySelectorAll('.contrast-item')].find(item => item.textContent?.includes(label))!.textContent!;
}
