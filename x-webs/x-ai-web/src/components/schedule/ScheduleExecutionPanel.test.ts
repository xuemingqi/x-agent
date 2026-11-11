// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createApp, defineComponent, h, nextTick, ref } from 'vue';

import { listScheduleExecutions } from '@/api/schedule';
import type { PageResult } from '@/api/types';
import type { ScheduleExecution, ScheduleTask } from '@/types/schedule';
import ScheduleExecutionPanel from './ScheduleExecutionPanel.vue';

vi.mock('@/api/schedule', () => ({ listScheduleExecutions: vi.fn() }));
vi.mock('@/components/file/FileDownloadCard.vue', () => ({ default: { template: '<div class="download-card" />' } }));

const cleanups: (() => void)[] = [];
const task = (id: string): ScheduleTask => ({
  id, taskName: `任务 ${id}`, prompt: '整理本周工作记录', modelId: '1', userId: '42',
  scheduleMode: 1, cronExpression: '0 0 9 * * ?', initialDelayMs: 0, status: 1,
  lastRunTime: null, nextRunTime: null, lastExecutionStatus: null, createTime: '', updateTime: '',
});
const execution = (id: string, text: string): ScheduleExecution => ({
  id, scheduleTaskId: '1', triggerSource: 2, scheduledFireTime: null, taskId: null, ownerUserId: '42',
  modelId: '1', status: 3, attemptCount: 1, startTime: null, finishTime: null, resultSummary: text,
  errorSummary: null, files: [], createTime: null, updateTime: null,
});
const result = (items: ScheduleExecution[], pageTotal = 1): PageResult<ScheduleExecution[]> => ({
  results: items, pageData: { page: 1, pageTotal, pageSize: 20, hasNextPages: pageTotal > 1 },
});

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(listScheduleExecutions).mockResolvedValue(result([]));
});
afterEach(() => cleanups.splice(0).forEach((cleanup) => cleanup()));

async function settle() {
  for (let step = 0; step < 8; step++) await nextTick();
}

async function mountPanel() {
  const container = document.createElement('div');
  document.body.append(container);
  const selected = ref(task('1'));
  const app = createApp(defineComponent({ setup: () => () => h(ScheduleExecutionPanel, { task: selected.value }) }));
  const wrapper = defineComponent({ setup: (_, { slots }) => () => h('div', slots.default?.()) });
  for (const name of ['el-icon', 'el-empty']) app.component(name, wrapper);
  app.component('el-button', defineComponent({
    props: ['loading'], setup: (props, { slots }) => () => h('button', { disabled: props.loading }, slots.default?.()),
  }));
  app.component('el-pagination', defineComponent({
    props: ['currentPage'], emits: ['update:currentPage'],
    setup: (props, { emit }) => () => h('button', {
      'aria-label': '下一页', onClick: () => emit('update:currentPage', Number(props.currentPage) + 1),
    }, '下一页'),
  }));
  app.directive('loading', {});
  app.mount(container);
  await settle();
  cleanups.push(() => { app.unmount(); container.remove(); });
  return { container, selected };
}

describe('Inline schedule execution history', () => {
  it('reads the result within the page and keeps provider HTML inert', async () => {
    vi.mocked(listScheduleExecutions).mockResolvedValue(result([execution('7', '# 本周记录\n<img src=x onerror=alert(1)>')]));
    const { container } = await mountPanel();

    expect(container.querySelector('.schedule-execution-workspace')).not.toBeNull();
    expect(container.querySelector('el-drawer')).toBeNull();
    expect(container.querySelector('.execution-markdown h1')?.textContent).toBe('本周记录');
    expect(container.querySelector('.execution-markdown img')).toBeNull();
    expect(container.textContent).toContain('整理本周工作记录');
    expect(listScheduleExecutions).toHaveBeenCalledWith('1', 1);
  });

  it('ignores a delayed result when a different task is selected', async () => {
    let complete!: (page: PageResult<ScheduleExecution[]>) => void;
    vi.mocked(listScheduleExecutions).mockImplementationOnce(() => new Promise((resolve) => { complete = resolve; }));
    const { container, selected } = await mountPanel();
    vi.mocked(listScheduleExecutions).mockResolvedValue(result([execution('2', '当前任务结果')]));
    selected.value = task('2');
    await settle();
    complete(result([execution('1', '上个任务的过期结果')]));
    await settle();

    expect(container.textContent).toContain('当前任务结果');
    expect(container.textContent).not.toContain('上个任务的过期结果');
  });

  it('pages results and resets to page one when changing task', async () => {
    vi.mocked(listScheduleExecutions).mockResolvedValue(result([execution('1', '第一页')], 3));
    const { container, selected } = await mountPanel();
    container.querySelector<HTMLButtonElement>('[aria-label="下一页"]')!.click();
    await settle();
    expect(listScheduleExecutions).toHaveBeenLastCalledWith('1', 2);

    selected.value = task('2');
    await settle();
    expect(listScheduleExecutions).toHaveBeenLastCalledWith('2', 1);
  });

  it('offers an inline retry when loading history fails', async () => {
    vi.mocked(listScheduleExecutions).mockRejectedValueOnce(new Error('记录服务不可用'));
    const { container } = await mountPanel();
    expect(container.querySelector('[role="alert"]')?.textContent).toContain('记录服务不可用');
    vi.mocked(listScheduleExecutions).mockResolvedValue(result([execution('1', '重试成功')]));
    [...container.querySelectorAll('button')].find((button) => button.textContent === '重新加载')!.click();
    await settle();

    expect(container.querySelector('[role="alert"]')).toBeNull();
    expect(container.textContent).toContain('重试成功');
  });
});
