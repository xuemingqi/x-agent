<script setup lang="ts">
import { Calendar, Clock, Cpu, EditPen, Refresh, Timer } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { computed, reactive, watch } from 'vue';

import type { AgentModel } from '@/types/model';
import type { ScheduleForm, ScheduleTask } from '@/types/schedule';
import { buildSchedulePayload } from '@/utils/schedule';

const props = defineProps<{
  visible: boolean;
  task: ScheduleTask | null;
  models: AgentModel[];
  saving: boolean;
}>();
const emit = defineEmits<{
  close: [];
  save: [payload: ScheduleForm];
}>();

const form = reactive<ScheduleForm>({
  taskName: '',
  modelId: '',
  prompt: '',
  scheduleMode: 1,
  cronExpression: '0 0 9 * * ?',
  fixedRateMs: 3600000,
  fixedDelayMs: 3600000,
  initialDelayMs: 0,
});
const title = computed(() => props.task ? '编辑定时任务' : '新建定时任务');
const scheduleModes = [
  { label: 'Cron 表达式', value: 1, icon: Calendar },
  { label: '固定频率', value: 2, icon: Refresh },
  { label: '固定延迟', value: 3, icon: Timer },
];

watch(() => props.visible, (visible) => {
  if (!visible) {
    return;
  }
  Object.assign(form, {
    taskName: props.task?.taskName || '',
    modelId: props.task?.modelId || props.models[0]?.id || '',
    prompt: props.task?.prompt || '',
    scheduleMode: props.task?.scheduleMode || 1,
    cronExpression: props.task?.cronExpression || '0 0 9 * * ?',
    fixedRateMs: props.task?.fixedRateMs || 3600000,
    fixedDelayMs: props.task?.fixedDelayMs || 3600000,
    initialDelayMs: props.task?.initialDelayMs || 0,
  });
});

function submit(): void {
  if (!form.taskName.trim() || !form.prompt.trim() || !form.modelId) {
    ElMessage.warning('请填写任务名称、执行内容并选择模型');
    return;
  }
  if (form.scheduleMode === 1 && !form.cronExpression?.trim()) {
    ElMessage.warning('请填写 Cron 表达式');
    return;
  }
  if (form.scheduleMode === 2 && !form.fixedRateMs) {
    ElMessage.warning('请填写固定频率');
    return;
  }
  if (form.scheduleMode === 3 && !form.fixedDelayMs) {
    ElMessage.warning('请填写固定延迟');
    return;
  }
  emit('save', buildSchedulePayload(form));
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="title"
    width="min(720px, 94vw)"
    class="content-editor-dialog schedule-dialog"
    align-center
    append-to-body
    destroy-on-close
    @close="emit('close')"
  >
    <template #header>
      <div class="content-dialog-heading">
        <div class="content-dialog-heading-icon"><el-icon><Calendar /></el-icon></div>
        <div>
          <span>AUTOMATION</span>
          <strong>{{ title }}</strong>
          <p>{{ task ? '调整任务内容与执行时间。' : '安排好重复工作，交给 Agent 自动完成。' }}</p>
        </div>
      </div>
    </template>

    <el-form label-position="top" class="content-editor-form schedule-form">
      <section class="content-form-section">
        <header class="content-form-section-heading">
          <span><el-icon><EditPen /></el-icon></span>
          <div>
            <strong>任务内容</strong>
            <p>告诉 Agent 需要完成什么，并选择执行模型。</p>
          </div>
        </header>

        <div class="form-grid">
          <el-form-item label="任务名称">
            <el-input v-model="form.taskName" maxlength="100" placeholder="例如：每天生成工作日报">
              <template #prefix><el-icon><EditPen /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item label="执行模型">
            <el-select v-model="form.modelId" placeholder="选择模型">
              <template #prefix><el-icon><Cpu /></el-icon></template>
              <el-option
                v-for="model in models"
                :key="model.id"
                :label="model.name || model.modelName"
                :value="model.id"
              />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="执行内容">
          <el-input
            v-model="form.prompt"
            type="textarea"
            :rows="4"
            maxlength="2000"
            resize="none"
            show-word-limit
            placeholder="描述 Agent 每次需要完成的任务，以及期望的输出结果"
          />
        </el-form-item>
      </section>

      <section class="content-form-section">
        <header class="content-form-section-heading">
          <span><el-icon><Clock /></el-icon></span>
          <div>
            <strong>执行计划</strong>
            <p>按指定时间或固定间隔，让任务自动重复执行。</p>
          </div>
        </header>

        <el-form-item label="调度方式">
          <el-segmented v-model="form.scheduleMode" class="schedule-mode-switch" :options="scheduleModes" block>
            <template #default="{ item }">
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.label }}</span>
            </template>
          </el-segmented>
        </el-form-item>
        <el-form-item v-if="form.scheduleMode === 1" label="Cron 表达式">
          <el-input v-model="form.cronExpression" placeholder="0 0 9 * * ?">
            <template #prefix><el-icon><Calendar /></el-icon></template>
          </el-input>
          <small class="field-tip">使用 Quartz Cron 格式，例如每天 9 点：0 0 9 * * ?</small>
        </el-form-item>
        <div v-else class="form-grid">
          <el-form-item v-if="form.scheduleMode === 2" label="执行频率">
            <el-input-number v-model="form.fixedRateMs" :min="1000" :step="60000" controls-position="right">
              <template #suffix>毫秒</template>
            </el-input-number>
            <small class="field-tip">按固定间隔触发，60000 毫秒为 1 分钟。</small>
          </el-form-item>
          <el-form-item v-else label="完成后延迟">
            <el-input-number v-model="form.fixedDelayMs" :min="1000" :step="60000" controls-position="right">
              <template #suffix>毫秒</template>
            </el-input-number>
            <small class="field-tip">上次执行完成后，等待设定时长再执行。</small>
          </el-form-item>
          <el-form-item label="首次执行延迟">
            <el-input-number v-model="form.initialDelayMs" :min="0" :step="60000" controls-position="right">
              <template #suffix>毫秒</template>
            </el-input-number>
            <small class="field-tip">设为 0 时无需等待首次执行。</small>
          </el-form-item>
        </div>
      </section>
    </el-form>

    <template #footer>
      <div class="content-dialog-footer">
        <span>保存后可在任务卡片中查看执行记录。</span>
        <div>
          <el-button @click="emit('close')">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submit">
            {{ task ? '保存修改' : '创建任务' }}
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>
