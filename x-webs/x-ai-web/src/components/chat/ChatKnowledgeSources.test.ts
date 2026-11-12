// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createApp, defineComponent, h, nextTick, ref } from 'vue';

import ChatKnowledgeSources from './ChatKnowledgeSources.vue';

const cleanups: (() => void)[] = [];

afterEach(() => cleanups.splice(0).forEach((cleanup) => cleanup()));

function mountPicker() {
  const container = document.createElement('div');
  const outside = document.createElement('button');
  outside.textContent = '输入框外的操作';
  document.body.append(container, outside);
  const state = ref({ selectedIds: [] as string[], disabled: false, saving: false });
  const onChange = vi.fn();
  const app = createApp({
    render: () => h(ChatKnowledgeSources, {
      knowledgeBases: [
        { id: 'common', name: '公共知识库', description: '', isDefault: true },
        { id: 'project', name: '项目文档', description: '项目需求和接口说明', isDefault: false },
      ],
      ...state.value,
      onChange,
    }),
  });
  app.component('el-icon', defineComponent({ setup: (_, { slots }) => () => h('span', slots.default?.()) }));
  app.component('RouterLink', defineComponent({ setup: (_, { slots }) => () => h('a', { href: '/knowledge' }, slots.default?.()) }));
  app.mount(container);
  cleanups.push(() => { app.unmount(); container.remove(); outside.remove(); });
  const trigger = container.querySelector<HTMLButtonElement>('button[aria-label="关联知识库"]')!;
  return { container, outside, state, onChange, trigger };
}

async function open(trigger: HTMLButtonElement): Promise<void> {
  trigger.click();
  await nextTick();
  await nextTick();
}

describe('composer knowledge picker', () => {
  it('opens a checklist above the composer, keeps common checked and focuses the first optional source', async () => {
    const { container, trigger, onChange } = mountPicker();
    await open(trigger);
    const common = container.querySelector<HTMLInputElement>('input[aria-label="公共知识库，自动关联"]')!;
    const project = container.querySelector<HTMLInputElement>('input[aria-label="项目文档"]')!;

    expect(container.querySelector('select, [role="combobox"]')).toBeNull();
    expect(trigger.getAttribute('aria-expanded')).toBe('true');
    expect(container.querySelector('[role="dialog"]')?.id).toBe(trigger.getAttribute('aria-controls'));
    expect(common.checked).toBe(true);
    expect(common.disabled).toBe(true);
    common.click();
    expect(onChange).not.toHaveBeenCalled();
    expect(document.activeElement).toBe(project);
  });

  it('waits for persisted selection and keeps the checklist open during saving', async () => {
    const { container, trigger, state, onChange } = mountPicker();
    await open(trigger);
    const project = container.querySelector<HTMLInputElement>('input[aria-label="项目文档"]')!;
    project.click();
    expect(onChange).toHaveBeenCalledExactlyOnceWith(['project']);
    expect(project.checked).toBe(false);

    state.value = { ...state.value, saving: true, disabled: true };
    await nextTick();
    expect(container.querySelector('[role="dialog"]')).not.toBeNull();
    expect(project.disabled).toBe(true);

    state.value = { selectedIds: ['project'], saving: false, disabled: false };
    await nextTick();
    expect(container.querySelector('[role="dialog"]')).not.toBeNull();
    expect(project.checked).toBe(true);
    expect(project.disabled).toBe(false);
  });

  it('closes on Escape and restores keyboard focus to its trigger', async () => {
    const { container, trigger } = mountPicker();
    await open(trigger);
    container.querySelector('input:not(:disabled)')!.dispatchEvent(new KeyboardEvent('keydown', {
      key: 'Escape', bubbles: true, cancelable: true,
    }));
    await nextTick();
    await nextTick();

    expect(container.querySelector('[role="dialog"]')).toBeNull();
    expect(trigger.getAttribute('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(trigger);
  });

  it('closes on outside pointer and when keyboard focus leaves the picker', async () => {
    const { container, outside, trigger } = mountPicker();
    await open(trigger);
    outside.dispatchEvent(new Event('pointerdown', { bubbles: true }));
    await nextTick();
    expect(container.querySelector('[role="dialog"]')).toBeNull();

    await open(trigger);
    outside.focus();
    await nextTick();
    expect(container.querySelector('[role="dialog"]')).toBeNull();
  });

  it('closes and prevents further changes when execution makes knowledge selection unavailable', async () => {
    const { container, state, trigger, onChange } = mountPicker();
    await open(trigger);
    state.value = { ...state.value, disabled: true };
    await nextTick();

    expect(container.querySelector('[role="dialog"]')).toBeNull();
    expect(trigger.disabled).toBe(true);
    trigger.click();
    await nextTick();
    expect(container.querySelector('[role="dialog"]')).toBeNull();
    expect(onChange).not.toHaveBeenCalled();
  });
});
