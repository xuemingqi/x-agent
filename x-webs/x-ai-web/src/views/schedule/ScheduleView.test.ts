// @vitest-environment jsdom
import { afterEach, expect, it, vi } from 'vitest';
import { createApp, defineComponent, h, nextTick } from 'vue';

import { listSchedules } from '@/api/schedule';
import ScheduleView from './ScheduleView.vue';

vi.mock('@/api/schedule', () => ({ listSchedules: vi.fn() }));
vi.mock('@/stores/chat', () => ({ useChatStore: () => ({ models: [], initialize: vi.fn().mockResolvedValue(undefined) }) }));
vi.mock('@/stores/model-setting', () => ({ useModelSettingStore: () => ({ requireConfigured: () => true }) }));
vi.mock('@/components/schedule/ScheduleFormDialog.vue', () => ({ default: { template: '<div />' } }));
vi.mock('@/components/schedule/ScheduleExecutionPanel.vue', () => ({ default: { template: '<section aria-label="执行记录" />' } }));
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn(), success: vi.fn() }, ElMessageBox: { confirm: vi.fn() } }));

const cleanups: (() => void)[] = [];
afterEach(() => cleanups.splice(0).forEach((cleanup) => cleanup()));

async function settle() {
  for (let step = 0; step < 8; step++) await nextTick();
}

it('opens execution history in the page and returns to the current task filter', async () => {
  vi.mocked(listSchedules).mockResolvedValue({
    results: [{ id: '1', taskName: '每周汇总', prompt: '整理工作记录', modelId: '1', userId: '42', scheduleMode: 1,
      cronExpression: '0 0 9 * * ?', initialDelayMs: 0, status: 1, lastRunTime: null, nextRunTime: null,
      lastExecutionStatus: null, createTime: '', updateTime: '' }],
    pageData: { page: 1, pageSize: 20, pageTotal: 1, hasNextPages: false },
  });
  const container = document.createElement('div');
  document.body.append(container);
  const app = createApp(ScheduleView);
  const wrapper = defineComponent({ setup: (_, { slots }) => () => h('div', slots.default?.()) });
  for (const name of ['el-icon', 'el-empty', 'el-option', 'el-select', 'el-dropdown']) app.component(name, wrapper);
  app.component('el-button', defineComponent({ setup: (_, { slots }) => () => h('button', slots.default?.()) }));
  app.component('el-input', defineComponent({
    props: ['modelValue'], emits: ['update:modelValue'], setup: (props, { emit }) => () => h('input', {
      value: props.modelValue,
      onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLInputElement).value),
    }),
  }));
  app.directive('loading', {});
  app.mount(container);
  cleanups.push(() => { app.unmount(); container.remove(); });
  await settle();
  const search = container.querySelector<HTMLInputElement>('input')!;
  search.value = '每周';
  search.dispatchEvent(new Event('input', { bubbles: true }));
  await settle();
  [...container.querySelectorAll('button')].find((button) => button.textContent === '执行记录')!.click();
  await settle();
  expect(container.querySelector('[aria-label="执行记录"]')).not.toBeNull();
  expect(container.querySelector('.schedule-grid')).toBeNull();
  expect(container.querySelector('el-drawer')).toBeNull();

  [...container.querySelectorAll('button')].find((button) => button.textContent === '返回任务')!.click();
  await settle();
  expect(container.querySelector<HTMLInputElement>('input')?.value).toBe('每周');
  expect(container.querySelector('.schedule-grid')?.textContent).toContain('每周汇总');
});
