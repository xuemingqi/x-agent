<script setup lang="ts">
import {
  ArrowLeft,
  CaretRight,
  Delete,
  Document,
  Edit,
  MoreFilled,
  Plus,
  Refresh,
  Search,
  VideoPause,
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { onMounted, reactive, ref } from 'vue';

import {
  createSchedule,
  deleteSchedule,
  listSchedules,
  pauseSchedule,
  resumeSchedule,
  runScheduleNow,
  updateSchedule,
} from '@/api/schedule';
import ScheduleExecutionPanel from '@/components/schedule/ScheduleExecutionPanel.vue';
import ScheduleFormDialog from '@/components/schedule/ScheduleFormDialog.vue';
import { useChatStore } from '@/stores/chat';
import { useModelSettingStore } from '@/stores/model-setting';
import type {
  ExecutionStatus,
  ScheduleForm,
  ScheduleMode,
  ScheduleStatus,
  ScheduleTask,
} from '@/types/schedule';

const chat = useChatStore();
const modelSetting = useModelSettingStore();
const loading = ref(false);
const saving = ref(false);
const tasks = ref<ScheduleTask[]>([]);
const pageTotal = ref(1);
const formVisible = ref(false);
const editingTask = ref<ScheduleTask | null>(null);
const executionTask = ref<ScheduleTask | null>(null);
const query = reactive<{
  page: number;
  pageSize: number;
  taskName: string;
  scheduleMode?: ScheduleMode;
  status?: ScheduleStatus;
}>({ page: 1, pageSize: 20, taskName: '' });

onMounted(async () => {
  try {
    await chat.initialize();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '模型加载失败');
  }
  await load();
});

async function load(): Promise<void> {
  loading.value = true;
  try {
    const result = await listSchedules(query);
    tasks.value = result.results;
    pageTotal.value = result.pageData.pageTotal;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '定时任务加载失败');
  } finally {
    loading.value = false;
  }
}

function openCreate(): void {
  if (!modelSetting.requireConfigured()) {
    return;
  }
  editingTask.value = null;
  formVisible.value = true;
}

function openEdit(task: ScheduleTask): void {
  editingTask.value = task;
  formVisible.value = true;
}

async function save(payload: ScheduleForm): Promise<void> {
  if (!modelSetting.requireConfigured()) {
    return;
  }
  saving.value = true;
  try {
    if (editingTask.value) {
      await updateSchedule(editingTask.value.id, payload);
      ElMessage.success('任务已更新');
    } else {
      await createSchedule(payload);
      ElMessage.success('任务已创建');
    }
    formVisible.value = false;
    await load();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败');
  } finally {
    saving.value = false;
  }
}

async function toggleStatus(task: ScheduleTask): Promise<void> {
  try {
    if (task.status === 1) {
      await pauseSchedule(task.id);
      ElMessage.success('任务已暂停');
    } else {
      await resumeSchedule(task.id);
      ElMessage.success('任务已恢复');
    }
    await load();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '状态修改失败');
  }
}

async function runNow(task: ScheduleTask): Promise<void> {
  if (!modelSetting.requireConfigured()) {
    return;
  }
  try {
    const execution = await runScheduleNow(task.id);
    ElMessage.success(`已触发执行，记录 ID：${execution.id}`);
    openExecutions(task);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '触发失败');
  }
}

async function remove(task: ScheduleTask): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除“${task.taskName}”吗？`, '删除定时任务', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    });
    await deleteSchedule(task.id);
    ElMessage.success('任务已删除');
    await load();
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '删除失败');
    }
  }
}

function openExecutions(task: ScheduleTask): void {
  executionTask.value = task;
}

function modeName(mode: number): string {
  return ({ 1: 'Cron', 2: '固定频率', 3: '固定延迟' } as Record<number, string>)[mode] || '未知';
}

function scheduleText(task: ScheduleTask): string {
  if (task.scheduleMode === 1) return task.cronExpression || '—';
  const duration = task.scheduleMode === 2 ? task.fixedRateMs : task.fixedDelayMs;
  return `${duration || 0} 毫秒`;
}

function formatTime(value: string | null): string {
  if (!value) return '尚未执行';
  return new Date(value).toLocaleString('zh-CN', { hour12: false });
}

function executionStatusClass(status: ExecutionStatus | null): string {
  if (status === 3) return 'success';
  if (status === 4 || status === 5) return 'failed';
  return status === 2 ? 'running' : 'idle';
}

function executionStatusText(status: ExecutionStatus | null): string {
  const statusText = {
    1: '等待执行',
    2: '执行中',
    3: '执行成功',
    4: '执行失败',
    5: '已中断',
  } as Record<number, string>;
  return statusText[status || 0] || '尚未执行';
}
</script>

<template>
  <section class="workspace-page schedule-page">
    <header class="page-header">
      <div>
        <p>{{ executionTask ? '定时任务 / 执行记录' : 'Automation' }}</p>
        <h1>{{ executionTask ? executionTask.taskName : '定时任务' }}</h1>
        <span>{{ executionTask ? '查看每次执行的结果、错误和生成文件' : '安排 Agent 自动完成重复工作' }}</span>
      </div>
      <el-button v-if="!executionTask" class="page-primary-action" type="primary" :icon="Plus" @click="openCreate">
        新建任务
      </el-button>
      <el-button v-else class="page-secondary-action" :icon="ArrowLeft" @click="executionTask = null">返回任务</el-button>
    </header>

    <ScheduleExecutionPanel v-if="executionTask" :task="executionTask" />
    <template v-else>
      <div class="filter-bar schedule-filter-bar">
        <el-input
          v-model="query.taskName"
          :prefix-icon="Search"
          aria-label="搜索任务名称"
          clearable
          placeholder="搜索任务名称"
          @keyup.enter="query.page = 1; load()"
          @clear="query.page = 1; load()"
        />
        <el-select
          v-model="query.scheduleMode"
          aria-label="调度方式"
          clearable
          placeholder="全部调度方式"
          @change="query.page = 1; load()"
        >
          <el-option label="Cron 表达式" :value="1" />
          <el-option label="固定频率" :value="2" />
          <el-option label="固定延迟" :value="3" />
        </el-select>
        <el-select
          v-model="query.status"
          aria-label="任务状态"
          clearable
          placeholder="全部状态"
          @change="query.page = 1; load()"
        >
          <el-option label="运行中" :value="1" />
          <el-option label="已暂停" :value="2" />
        </el-select>
        <el-button class="schedule-secondary-action" :icon="Refresh" :loading="loading" @click="load">
          刷新
        </el-button>
      </div>

      <div v-loading="loading" class="schedule-grid">
        <article v-for="task in tasks" :key="task.id" class="schedule-card schedule-row">
          <div class="schedule-card-head">
            <div class="schedule-status" :class="executionStatusClass(task.lastExecutionStatus)"
              :title="executionStatusText(task.lastExecutionStatus)"><i /></div>
            <div class="schedule-title">
              <h3><button type="button" @click="openExecutions(task)">{{ task.taskName }}</button></h3>
              <p>{{ task.prompt }}</p>
            </div>
          </div>
          <div class="schedule-row-timing">
            <div class="schedule-meta"><span>{{ modeName(task.scheduleMode) }}</span><code>{{ scheduleText(task) }}</code></div>
            <dl>
              <div><dt>下次执行</dt><dd>{{ task.status === 2 ? '已暂停' : formatTime(task.nextRunTime) }}</dd></div>
              <div><dt>最近执行</dt><dd>{{ formatTime(task.lastRunTime) }}</dd></div>
            </dl>
          </div>
          <div class="schedule-row-controls">
            <span class="schedule-state" :class="{ paused: task.status === 2 }">
              <i />{{ task.status === 1 ? '运行中' : '已暂停' }}
            </span>
            <div class="schedule-actions">
              <el-button text :icon="Document" @click="openExecutions(task)">执行记录</el-button>
              <el-button class="schedule-run-action" type="primary" plain :icon="CaretRight" @click="runNow(task)">立即执行</el-button>
              <el-dropdown trigger="click">
                <button class="icon-button" :aria-label="`${task.taskName}的更多操作`"><el-icon><MoreFilled /></el-icon></button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item :icon="Edit" @click="openEdit(task)">编辑</el-dropdown-item>
                    <el-dropdown-item :icon="task.status === 1 ? VideoPause : CaretRight" @click="toggleStatus(task)">
                      {{ task.status === 1 ? '暂停' : '恢复' }}
                    </el-dropdown-item>
                    <el-dropdown-item :icon="Delete" divided @click="remove(task)">删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>
        </article>
        <el-empty v-if="!loading && !tasks.length" :image-size="56" description="还没有定时任务">
          <template #image>
            <span class="empty-state-icon" aria-hidden="true"><el-icon><Document /></el-icon></span>
          </template>
          <el-button class="page-primary-action" type="primary" :icon="Plus" @click="openCreate">
            创建第一个任务
          </el-button>
        </el-empty>
      </div>

      <el-pagination
        v-if="pageTotal > 1"
        v-model:current-page="query.page"
        layout="prev, pager, next"
        :page-count="pageTotal"
        @current-change="load"
      />
    </template>

    <ScheduleFormDialog
      :visible="formVisible"
      :task="editingTask"
      :models="chat.models"
      :saving="saving"
      @close="formVisible = false"
      @save="save"
    />
  </section>
</template>

<style scoped>
.schedule-page .page-header > div { min-width: 0; }
.schedule-page .page-header > .el-button { flex-shrink: 0; }
.schedule-page .page-header h1 { overflow-wrap: anywhere; }
.schedule-filter-bar { padding: 0 0 20px; border: 0; border-radius: 0; background: transparent; }
.schedule-grid { display: flex; flex-direction: column; gap: 0; min-height: 0; }

.schedule-row {
  display: grid;
  grid-template-columns: minmax(220px, 1.1fr) minmax(240px, 1fr) auto;
  gap: 24px;
  align-items: center;
  padding: 24px 0;
  border: 0;
  border-top: 1px solid var(--line);
  border-radius: 0;
  background: transparent;
}

.schedule-card-head { min-width: 0; gap: 12px; }
.schedule-status { flex: 0 0 8px; width: 8px; height: 8px; margin-top: 8px; border-radius: 50%; background: transparent; }
.schedule-status.running i { background: var(--primary); }
.schedule-title h3 { margin: 0 0 8px; font-size: 15px; font-weight: 500; line-height: 24px; }
.schedule-title h3 button { padding: 0; border: 0; color: var(--text); background: transparent; font: inherit; text-align: left; cursor: pointer; overflow-wrap: anywhere; }
.schedule-title h3 button:hover { color: var(--primary); }
.schedule-title h3 button:focus-visible { outline: 2px solid var(--primary); outline-offset: 4px; border-radius: 4px; }
.schedule-title p { min-height: 0; font-size: 13px; line-height: 20px; overflow-wrap: anywhere; }
.schedule-row-timing { min-width: 0; }
.schedule-meta { margin: 0 0 12px; flex-wrap: wrap; }
.schedule-meta span { padding: 0; color: var(--text-soft); background: transparent; font-size: 12px; }
.schedule-meta code { color: var(--text-muted); font-size: 12px; overflow-wrap: anywhere; }
.schedule-card dl { gap: 8px 20px; }
.schedule-card dl div { padding: 0; border-radius: 0; background: transparent; }
.schedule-card dt, .schedule-card dd { font-size: 12px; }
.schedule-card dd { line-height: 20px; font-variant-numeric: tabular-nums; }
.schedule-row-controls { display: flex; align-items: flex-end; flex-direction: column; gap: 12px; }
.schedule-state { margin-right: 0; font-size: 12px; }
.schedule-actions { margin: 0; padding: 0; border: 0; gap: 4px; flex-wrap: wrap; }
.schedule-actions .el-button { padding: 0 8px; }
.schedule-grid > .el-empty { min-height: 280px; }

@container workspace (max-width: 1100px) {
  .schedule-row { grid-template-columns: minmax(0, 1fr) auto; gap: 16px 24px; }
  .schedule-card-head { grid-column: 1 / -1; }
  .schedule-row-controls { align-self: end; }
}

@container workspace (max-width: 640px) {
  .schedule-filter-bar { grid-template-columns: minmax(0, 1fr); }
  .schedule-row { grid-template-columns: minmax(0, 1fr); }
  .schedule-row-controls { align-items: stretch; flex-direction: row; align-items: center; justify-content: space-between; flex-wrap: wrap; }
  .schedule-card dl { grid-template-columns: minmax(0, 1fr); }
  .schedule-actions { justify-content: flex-start; }
}
</style>
