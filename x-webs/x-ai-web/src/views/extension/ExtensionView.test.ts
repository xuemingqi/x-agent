// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createApp, defineComponent, h, nextTick } from 'vue';
import { createPinia } from 'pinia';
import { ElMessage } from 'element-plus';

import { importSkillArchive, listSkills, setSkillEnabled } from '@/api/skill';
import { useModelSettingStore } from '@/stores/model-setting';
import type { AgentSkill } from '@/types/skill';
import ExtensionView from './ExtensionView.vue';

vi.mock('@/api/skill', () => ({
  listSkills: vi.fn().mockResolvedValue([]),
  setSkillEnabled: vi.fn(),
  importSkillArchive: vi.fn(),
  exportSkillArchive: vi.fn(),
}));
vi.mock('@/components/extension/McpManagementPanel.vue', () => ({ default: { template: '<div />' } }));
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() },
}));

const importedSkill: AgentSkill = {
  name: 'research', description: '调研', content: '# 调研',
  enabled: true, resourcePaths: ['scripts/run.py'],
};
const cleanups: (() => void)[] = [];

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(listSkills).mockResolvedValue([]);
  vi.mocked(setSkillEnabled).mockImplementation(async (_, enabled) => enabled);
});

afterEach(() => cleanups.splice(0).forEach((cleanup) => cleanup()));

async function settle() {
  for (let step = 0; step < 8; step++) await nextTick();
}

async function mountView(configured = true) {
  const container = document.createElement('div');
  document.body.append(container);
  const pinia = createPinia();
  const app = createApp(ExtensionView).use(pinia);
  const modelSetting = useModelSettingStore(pinia);
  modelSetting.loaded = true;
  modelSetting.models = configured ? [{ id: '42', name: 'My Model', modelName: 'model', modelUrl: 'https://example.com/v1', contextWindowSize: 32768, temperature: null, isDefault: true }] : [];
  const wrapper = defineComponent({ setup: (_, { slots }) => () => h('div', slots.default?.()) });
  const button = defineComponent({
    props: ['loading', 'disabled'],
    setup: (props, { slots }) => () => h('button', { disabled: props.loading || props.disabled }, slots.default?.()),
  });
  const dialog = defineComponent({ setup: () => () => null });
  for (const name of ['el-icon', 'el-tag', 'el-empty', 'el-dropdown', 'el-tooltip']) app.component(name, wrapper);
  app.component('el-switch', defineComponent({
    props: ['modelValue', 'loading'], emits: ['change'],
    setup: (props, { emit }) => () => h('button', {
      role: 'switch', 'aria-checked': String(props.modelValue), disabled: props.loading,
      onClick: () => emit('change', !props.modelValue),
    }),
  }));
  app.component('el-dialog', dialog);
  app.component('el-button', button);
  app.directive('loading', {});
  app.mount(container);
  await settle();
  cleanups.push(() => { app.unmount(); container.remove(); });
  const input = container.querySelector<HTMLInputElement>('input[type="file"]')!;
  return { container, input, modelSetting };
}

function selectFile(input: HTMLInputElement, file: File) {
  Object.defineProperty(input, 'files', { value: [file], configurable: true });
  input.dispatchEvent(new Event('change', { bubbles: true }));
}

describe('Skill archive import', () => {
  it('opens model settings before allowing import when the default model is missing', async () => {
    const { container, input, modelSetting } = await mountView(false);
    const chooseFile = vi.spyOn(input, 'click');
    Array.from(container.querySelectorAll('button')).find((button) => button.textContent?.includes('导入 Skill'))!.click();
    await settle();

    expect(modelSetting.dialogVisible).toBe(true);
    expect(chooseFile).not.toHaveBeenCalled();
    expect(importSkillArchive).not.toHaveBeenCalled();
  });

  it('shows progress, avoids duplicate uploads, and refreshes personal skills on completion', async () => {
    let complete!: (skill: AgentSkill) => void;
    vi.mocked(importSkillArchive).mockImplementation(() => new Promise((resolve) => { complete = resolve; }));
    const { container, input } = await mountView();
    const file = new File(['archive'], 'research.tar', { type: 'application/x-tar' });
    selectFile(input, file);
    await settle();
    expect(container.textContent).toContain('默认模型整理中');
    selectFile(input, file);
    expect(importSkillArchive).toHaveBeenCalledExactlyOnceWith(file);

    vi.mocked(listSkills).mockResolvedValue([importedSkill]);
    complete(importedSkill);
    await settle();

    expect(container.textContent).toContain('research');
    expect(container.textContent).not.toContain('默认模型整理中');
    expect(container.textContent).toContain('导出');
    expect(listSkills).toHaveBeenCalledTimes(2);
  });

  it('requires a personal default model even when another personal model exists', async () => {
    const { container, input, modelSetting } = await mountView();
    modelSetting.models[0].isDefault = false;
    const chooseFile = vi.spyOn(input, 'click');
    Array.from(container.querySelectorAll('button')).find((button) => button.textContent?.includes('导入 Skill'))!.click();
    await settle();

    expect(modelSetting.dialogVisible).toBe(true);
    expect(chooseFile).not.toHaveBeenCalled();
    expect(importSkillArchive).not.toHaveBeenCalled();
  });

  it('allows retrying the same archive after a model failure', async () => {
    vi.mocked(importSkillArchive).mockRejectedValueOnce(new Error('模型整理失败')).mockResolvedValue(importedSkill);
    const { input, container } = await mountView();
    const file = new File(['archive'], 'research.zip');
    selectFile(input, file);
    await settle();
    expect(input.value).toBe('');
    expect(container.textContent).not.toContain('默认模型整理中');
    selectFile(input, file);
    await settle();

    expect(importSkillArchive).toHaveBeenCalledTimes(2);
  });

  it.each(['skill.txt', 'skill.rar'])('rejects unsupported %s uploads', async (name) => {
    const { input } = await mountView();
    selectFile(input, new File(['archive'], name));
    await settle();
    expect(importSkillArchive).not.toHaveBeenCalled();
  });
});

describe('Skill enablement', () => {
  it('allows toggling an owned Skill and exposes editing without source tabs', async () => {
    vi.mocked(listSkills).mockResolvedValue([importedSkill]);
    const { container } = await mountView();
    expect(container.querySelector('.scope-switch')).toBeNull();
    expect(container.textContent).toContain('编辑');
    expect(container.textContent).not.toContain('分享');
    expect(container.textContent).not.toContain('邀请');
    const toggle = container.querySelector<HTMLButtonElement>('[role="switch"]')!;
    toggle.click();
    await settle();
    expect(setSkillEnabled).toHaveBeenCalledWith(importedSkill.name, false);
    expect(toggle.getAttribute('aria-checked')).toBe('false');
    toggle.click();
    await settle();
    expect(toggle.getAttribute('aria-checked')).toBe('true');
  });

  it('keeps the original state on failure and prevents repeat submissions while saving', async () => {
    vi.mocked(listSkills).mockResolvedValue([{ ...importedSkill }]);
    let reject!: (error: Error) => void;
    vi.mocked(setSkillEnabled).mockImplementation(() => new Promise((_, rejectRequest) => { reject = rejectRequest; }));
    const { container } = await mountView();
    const toggle = container.querySelector<HTMLButtonElement>('[role="switch"]')!;
    toggle.click();
    await settle();
    expect(toggle.disabled).toBe(true);
    toggle.click();
    expect(setSkillEnabled).toHaveBeenCalledTimes(1);
    reject(new Error('保存失败'));
    await settle();
    expect(toggle.getAttribute('aria-checked')).toBe('true');
    expect(toggle.disabled).toBe(false);
    expect(ElMessage.error).toHaveBeenCalledWith('保存失败');
  });
});
