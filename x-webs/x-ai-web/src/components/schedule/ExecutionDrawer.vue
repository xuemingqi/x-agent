<script setup lang="ts">
import { RefreshRight } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { ref, watch } from 'vue';

import { listScheduleExecutions } from '@/api/schedule';
import FileDownloadCard from '@/components/file/FileDownloadCard.vue';
import type { ScheduleExecution, ScheduleTask } from '@/types/schedule';
import { renderMarkdown } from '@/utils/markdown';

const props = defineProps<{
  visible: boolean;
  task: ScheduleTask | null;
}>();
const emit = defineEmits<{ close: [] }>();

const loading = ref(false);
const executions = ref<ScheduleExecution[]>([]);

watch(() => [props.visible, props.task?.id], async () => {
  if (props.visible && props.task) {
    await load();
  }
});

async function load(): Promise<void> {
  if (!props.task) {
    return;
  }
  loading.value = true;
  try {
    const page = await listScheduleExecutions(props.task.id);
    executions.value = page.results;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '执行记录加载失败');
  } finally {
    loading.value = false;
  }
}

function statusName(status: number): string {
  return ({ 1: '等待执行', 2: '执行中', 3: '执行成功', 4: '执行失败', 5: '已中断' } as Record<number, string>)[status] || '未知';
}

function statusType(status: number): 'info' | 'warning' | 'success' | 'danger' {
  if (status === 3) return 'success';
  if (status === 4 || status === 5) return 'danger';
  if (status === 2) return 'warning';
  return 'info';
}

function formatTime(value: string | null): string {
  if (!value) return '—';
  return new Date(value).toLocaleString('zh-CN', { hour12: false });
}
</script>

<template>
  <el-drawer
    :model-value="visible"
    :title="`${task?.taskName || ''} · 执行记录`"
    size="min(720px, 92vw)"
    class="execution-drawer"
    @close="emit('close')"
  >
    <div class="drawer-toolbar">
      <span>执行结果独立保存，不会阻塞聊天请求。</span>
      <el-button class="schedule-secondary-action" :icon="RefreshRight" :loading="loading" @click="load">
        刷新
      </el-button>
    </div>
    <div v-loading="loading" class="execution-list">
      <article v-for="execution in executions" :key="execution.id" class="execution-card">
        <div class="execution-heading">
          <div>
            <el-tag :type="statusType(execution.status)" effect="light" round>
              {{ statusName(execution.status) }}
            </el-tag>
            <span>#{{ execution.id }}</span>
          </div>
          <time>{{ formatTime(execution.startTime || execution.createTime) }}</time>
        </div>
        <dl>
          <div><dt>触发方式</dt><dd>{{ execution.triggerSource === 2 ? '手动触发' : '定时触发' }}</dd></div>
          <div><dt>执行次数</dt><dd>{{ execution.attemptCount || 0 }}</dd></div>
          <div><dt>结束时间</dt><dd>{{ formatTime(execution.finishTime) }}</dd></div>
        </dl>
        <div v-if="execution.resultSummary" class="execution-result success">
          <strong>执行结果</strong>
          <div class="execution-markdown markdown-body" v-html="renderMarkdown(execution.resultSummary)" />
        </div>
        <div v-if="execution.errorSummary" class="execution-result error">
          <strong>错误信息</strong>
          <div class="execution-markdown markdown-body" v-html="renderMarkdown(execution.errorSummary)" />
        </div>
        <div v-if="execution.files?.length" class="execution-files">
          <strong>生成文件</strong>
          <div class="file-download-list">
            <FileDownloadCard
              v-for="file in execution.files"
              :key="file.id"
              :file="file"
              description="定时任务生成"
            />
          </div>
        </div>
      </article>
      <el-empty v-if="!loading && !executions.length" description="还没有执行记录" />
    </div>
  </el-drawer>
</template>
