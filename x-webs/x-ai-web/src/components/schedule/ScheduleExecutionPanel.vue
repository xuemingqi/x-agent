<script setup lang="ts">
import { Document, RefreshRight } from '@element-plus/icons-vue';
import { onUnmounted, ref, watch } from 'vue';

import { listScheduleExecutions } from '@/api/schedule';
import FileDownloadCard from '@/components/file/FileDownloadCard.vue';
import type { ScheduleExecution, ScheduleTask } from '@/types/schedule';
import { renderMarkdown } from '@/utils/markdown';

const props = defineProps<{ task: ScheduleTask }>();
const loading = ref(false);
const error = ref('');
const executions = ref<ScheduleExecution[]>([]);
const page = ref(1);
const pageTotal = ref(1);
let revision = 0;
let disposed = false;

watch(() => props.task.id, () => { page.value = 1; }, { flush: 'sync' });
watch([() => props.task.id, page], load, { immediate: true });
onUnmounted(() => { disposed = true; });

async function load(): Promise<void> {
  const requestRevision = ++revision;
  const taskId = props.task.id;
  loading.value = true;
  error.value = '';
  executions.value = [];
  try {
    const result = await listScheduleExecutions(taskId, page.value);
    if (disposed || requestRevision !== revision) return;
    executions.value = result.results;
    pageTotal.value = result.pageData.pageTotal;
  } catch (cause) {
    if (!disposed && requestRevision === revision) {
      error.value = cause instanceof Error ? cause.message : '执行记录加载失败';
    }
  } finally {
    if (!disposed && requestRevision === revision) loading.value = false;
  }
}

function statusName(status: number): string {
  return ({ 1: '等待执行', 2: '执行中', 3: '执行成功', 4: '执行失败', 5: '已中断' } as Record<number, string>)[status] || '未知';
}

function statusClass(status: number): string {
  if (status === 3) return 'success';
  if (status === 4 || status === 5) return 'failed';
  return status === 2 ? 'running' : 'pending';
}

function formatTime(value: string | null): string {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—';
}

function cadence(task: ScheduleTask): string {
  if (task.scheduleMode === 1) return task.cronExpression || '—';
  const milliseconds = task.scheduleMode === 2 ? task.fixedRateMs : task.fixedDelayMs;
  return `${task.scheduleMode === 2 ? '固定频率' : '固定延迟'} · ${milliseconds || 0} 毫秒`;
}
</script>

<template>
  <div class="schedule-execution-workspace">
    <aside class="execution-task-summary" aria-label="任务信息">
      <h2>任务信息</h2>
      <span class="task-state" :class="{ paused: task.status === 2 }">{{ task.status === 1 ? '运行中' : '已暂停' }}</span>
      <dl>
        <div><dt>调度规则</dt><dd>{{ cadence(task) }}</dd></div>
        <div><dt>下次执行</dt><dd>{{ formatTime(task.nextRunTime) }}</dd></div>
        <div><dt>最近执行</dt><dd>{{ formatTime(task.lastRunTime) }}</dd></div>
      </dl>
      <details class="execution-task-prompt">
        <summary>任务指令</summary>
        <p>{{ task.prompt }}</p>
      </details>
    </aside>

    <section class="execution-history" aria-label="执行记录">
      <header class="execution-history-toolbar">
        <div><h2>执行记录</h2><span>第 {{ page }} 页</span></div>
        <el-button text :icon="RefreshRight" :loading="loading" @click="load">刷新</el-button>
      </header>
      <div v-loading="loading" class="execution-list" aria-live="polite">
        <div v-if="error" class="execution-load-error" role="alert">
          <p>{{ error }}</p>
          <el-button text @click="load">重新加载</el-button>
        </div>
        <article v-for="execution in executions" :key="execution.id" class="execution-entry">
          <header class="execution-entry-heading">
            <div>
              <span class="execution-state" :class="statusClass(execution.status)">{{ statusName(execution.status) }}</span>
              <span class="execution-number">#{{ execution.id }}</span>
            </div>
            <time>{{ formatTime(execution.startTime || execution.createTime) }}</time>
          </header>
          <dl class="execution-metadata">
            <div><dt>触发方式</dt><dd>{{ execution.triggerSource === 2 ? '手动触发' : '定时触发' }}</dd></div>
            <div><dt>执行次数</dt><dd>{{ execution.attemptCount || 0 }}</dd></div>
            <div><dt>结束时间</dt><dd>{{ formatTime(execution.finishTime) }}</dd></div>
          </dl>
          <section v-if="execution.resultSummary" class="execution-output">
            <h3>执行结果</h3>
            <div class="execution-markdown markdown-body" v-html="renderMarkdown(execution.resultSummary)" />
          </section>
          <section v-if="execution.errorSummary" class="execution-output execution-error">
            <h3>错误信息</h3>
            <div class="execution-markdown markdown-body" v-html="renderMarkdown(execution.errorSummary)" />
          </section>
          <section v-if="execution.files?.length" class="execution-output">
            <h3>生成文件</h3>
            <div class="file-download-list">
              <FileDownloadCard v-for="file in execution.files" :key="file.id" :file="file" description="定时任务生成" />
            </div>
          </section>
          <p v-if="!execution.resultSummary && !execution.errorSummary && !execution.files?.length" class="execution-pending">
            {{ execution.status === 1 || execution.status === 2 ? '任务完成后将在这里显示结果' : '本次执行未生成文本或文件' }}
          </p>
        </article>
        <el-empty v-if="!loading && !error && !executions.length" :image-size="56" description="还没有执行记录">
          <template #image>
            <span class="empty-state-icon" aria-hidden="true"><el-icon><Document /></el-icon></span>
          </template>
        </el-empty>
      </div>
      <el-pagination v-if="pageTotal > 1" v-model:current-page="page" :page-count="pageTotal"
        layout="prev, pager, next" :disabled="loading" />
    </section>
  </div>
</template>

<style scoped>
.schedule-execution-workspace {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: 32px;
  align-items: start;
  width: min(var(--workspace-max-width), 100%);
  min-width: 0;
  margin: 0 auto;
}

.execution-task-summary {
  position: sticky;
  top: 0;
  min-width: 0;
  padding: 24px 24px 24px 0;
  border-right: 1px solid var(--line);
}

h2, h3 { margin: 0; color: var(--text); font-weight: 500; }
h2 { font-size: 15px; line-height: 24px; }
h3 { font-size: 13px; }

.task-state, .execution-state {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--success-text);
  font-size: 12px;
}

.task-state { margin-top: 12px; }
.task-state::before, .execution-state::before {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  content: '';
}

.task-state.paused, .execution-state.pending { color: var(--text-muted); }
.execution-state.failed { color: var(--danger-text); }
.execution-state.running { color: var(--primary); }

.execution-task-summary dl {
  display: grid;
  gap: 20px;
  margin: 24px 0;
}

dt { color: var(--text-muted); font-size: 12px; }
dd { margin: 8px 0 0; color: var(--text-soft); font-size: 12px; line-height: 1.7; overflow-wrap: anywhere; }

.execution-task-prompt { padding-top: 20px; border-top: 1px solid var(--line); }
.execution-task-prompt summary { color: var(--text-soft); font-size: 13px; cursor: pointer; }
.execution-task-prompt p { margin: 12px 0 0; color: var(--text-muted); font-size: 13px; line-height: 1.8; white-space: pre-wrap; overflow-wrap: anywhere; }
.execution-history { min-width: 0; }
.execution-history-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; min-height: 56px; }
.execution-history-toolbar > div { display: flex; align-items: center; gap: 12px; }
.execution-history-toolbar span, .execution-number, time { color: var(--text-muted); font-size: 12px; font-variant-numeric: tabular-nums; }
.execution-list { min-height: 240px; }

.execution-entry {
  margin-bottom: 16px;
  padding: 24px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface);
}

.execution-entry-heading, .execution-entry-heading > div { display: flex; align-items: center; gap: 12px; }
.execution-entry-heading { justify-content: space-between; flex-wrap: wrap; }
.execution-number { overflow-wrap: anywhere; }
.execution-metadata { display: flex; flex-wrap: wrap; gap: 16px 32px; margin: 20px 0 0; }
.execution-metadata > div { display: flex; align-items: baseline; gap: 8px; }
.execution-metadata dd { margin: 0; font-variant-numeric: tabular-nums; }
.execution-output { margin-top: 24px; padding-top: 20px; border-top: 1px solid var(--line); }
.execution-output h3 { margin-bottom: 12px; }
.execution-error h3, .execution-load-error { color: var(--danger-text); }
.execution-markdown { margin: 0; overflow-wrap: anywhere; }
.execution-markdown :deep(pre) { max-width: 100%; overflow-x: auto; }
.execution-pending { margin: 24px 0 0; color: var(--text-muted); font-size: 13px; }
.execution-load-error { padding: 24px; border: 1px solid var(--line); border-radius: 12px; font-size: 13px; }
.el-pagination { justify-content: flex-end; margin-top: 24px; }

@container workspace (max-width: 1000px) {
  .schedule-execution-workspace { grid-template-columns: minmax(0, 1fr); gap: 16px; }
  .execution-task-summary { position: static; padding: 20px; border: 1px solid var(--line); border-radius: 12px; }
  .execution-task-summary dl { grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; margin: 20px 0; }
}

@container workspace (max-width: 600px) {
  .execution-entry { padding: 20px; }
  .execution-task-summary dl { grid-template-columns: minmax(0, 1fr); }
  .execution-entry-heading > div { flex-wrap: wrap; }
  .execution-metadata { flex-direction: column; gap: 12px; }
}
</style>
