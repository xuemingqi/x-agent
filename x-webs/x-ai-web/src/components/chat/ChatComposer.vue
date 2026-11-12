<script setup lang="ts">
import {
  ArrowDown,
  ArrowUp,
  Check,
  Close,
  Clock,
  Delete,
  Document,
  EditPen,
  Loading,
  MagicStick,
  Picture,
  Plus,
  Promotion,
  Right,
  VideoPause,
} from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import { listSkills } from '@/api/skill';
import FileImagePreview from '@/components/file/FileImagePreview.vue';
import type { QueuedMessage } from '@/stores/chat';
import type { ConversationFile } from '@/types/chat';
import { supportsImageInput, supportsImageOutput } from '@/types/model';
import type { AgentModel } from '@/types/model';
import { isImageFile } from '@/utils/conversation-file';
import type { AgentSkill } from '@/types/skill';

const props = defineProps<{
  models: AgentModel[];
  modelId: string;
  modelConfigured: boolean;
  sending: boolean;
  files: ConversationFile[];
  uploadingFiles: boolean;
  queuedMessages?: QueuedMessage[];
  interrupting?: boolean;
  queuePaused?: boolean;
  loading?: boolean;
}>();
const emit = defineEmits<{
  send: [content: string];
  stop: [];
  steer: [messageId: string];
  editQueuedMessage: [messageId: string];
  updateQueuedDraft: [messageId: string, content: string];
  finishQueuedEdit: [messageId: string, save: boolean];
  removeQueuedMessage: [messageId: string];
  upload: [files: File[]];
  removeFile: [fileId: string];
  configureModel: [];
  'update:modelId': [modelId: string];
}>();

const content = ref('');
const QUEUE_COLLAPSE_THRESHOLD = 2;
const queueElement = ref<HTMLElement | null>(null);
const queueExpanded = ref(false);
const queueCollapsible = computed(() => (props.queuedMessages?.length ?? 0) > QUEUE_COLLAPSE_THRESHOLD);
const queueCollapsed = computed(() => queueCollapsible.value && !queueExpanded.value);

function collapseQueueOutside(event: PointerEvent): void {
  if (!queueElement.value?.contains(event.target as Node)) {
    queueExpanded.value = false;
  }
}

onMounted(() => document.addEventListener('pointerdown', collapseQueueOutside, true));
onBeforeUnmount(() => document.removeEventListener('pointerdown', collapseQueueOutside, true));
watch(queueCollapsible, () => { queueExpanded.value = false; });

const editingMessage = computed(() => props.queuedMessages?.find((message) => message.draft !== undefined));
// 编辑队列消息时保留主输入框原有草稿，完成后恢复。
const composerContent = computed({
  get: () => editingMessage.value?.draft ?? content.value,
  set: (value: string) => {
    if (editingMessage.value) {
      emit('updateQueuedDraft', editingMessage.value.id, value);
    } else {
      content.value = value;
    }
  },
});
const composerFiles = computed(() => editingMessage.value?.files ?? props.files);
const composerModelId = computed(() => editingMessage.value?.modelId ?? props.modelId);
const editDisabled = computed(() => Boolean(editingMessage.value && (editingMessage.value.removing || props.interrupting)));
const isComposing = ref(false);
let submitAfterComposition = false;
const fileInput = ref<HTMLInputElement | null>(null);
const textarea = ref<HTMLTextAreaElement | null>(null);
const skills = ref<AgentSkill[]>([]);
const selectedSkill = ref<AgentSkill | null>(null);
const skillsLoading = ref(false);
const skillMenuDismissed = ref(false);
const skillCommandList = ref<HTMLElement | null>(null);
const highlightedSkillIndex = ref(0);
const selectedModel = computed(() => props.models.find((model) => model.id === composerModelId.value));
const selectedModelLabel = computed(() => selectedModel.value?.name || selectedModel.value?.modelName || '选择模型');
const modelSelectWidth = computed(() => {
  const textWidth = Array.from(selectedModelLabel.value).reduce(
    (width, character) => width + (/^[\x00-\xff]$/.test(character) ? 7 : 12),
    0,
  );
  return `${Math.min(240, Math.max(64, textWidth + 38))}px`;
});
const canSend = computed(() =>
  Boolean(
    (composerContent.value.trim() || isComposing.value || (!editingMessage.value && selectedSkill.value) || composerFiles.value.length)
    && composerModelId.value
    && props.modelConfigured
    && !props.loading
    && !editDisabled.value
    && (editingMessage.value || !props.uploadingFiles),
  ),
);
const skillCommandQuery = computed<string | null>(() => {
  if (editingMessage.value) {
    return null;
  }
  const match = content.value.match(/^\/([^\s]*)$/);
  return match ? match[1].toLowerCase() : null;
});
const matchedSkills = computed(() => {
  const query = skillCommandQuery.value;
  if (query === null) {
    return [];
  }
  return skills.value.filter((skill) =>
    `${skill.name} ${skill.description}`.toLowerCase().includes(query),
  );
});
const skillMenuVisible = computed(() =>
  Boolean(!selectedSkill.value && skillCommandQuery.value !== null && !skillMenuDismissed.value),
);

watch(skillCommandQuery, (query, previousQuery) => {
  highlightedSkillIndex.value = 0;
  void nextTick(() => {
    if (skillCommandList.value) {
      skillCommandList.value.scrollTop = 0;
    }
  });
  if (query !== null && previousQuery === null) {
    void loadAvailableSkills();
  }
});

watch(() => editingMessage.value?.id, () => {
  submitAfterComposition = false;
  void nextTick(() => textarea.value?.focus());
});

/**
 * 加载当前用户已启用的 Skill。
 */
async function loadAvailableSkills(): Promise<void> {
  skillsLoading.value = true;
  try {
    skills.value = await listSkills();
  } catch {
    ElMessage.error('Skill 列表加载失败');
  } finally {
    skillsLoading.value = false;
  }
}

function submit(): void {
  if (!canSend.value) {
    return;
  }
  if (isComposing.value) {
    submitAfterComposition = true;
    textarea.value?.blur();
    return;
  }
  if (editingMessage.value) {
    emit('finishQueuedEdit', editingMessage.value.id, true);
    return;
  }
  const skillCommand = selectedSkill.value ? `/${selectedSkill.value.name}` : '';
  emit('send', [skillCommand, content.value.trim()].filter(Boolean).join(' '));
  content.value = '';
  selectedSkill.value = null;
  skillMenuDismissed.value = true;
}

function handleCompositionEnd(): void {
  isComposing.value = false;
  if (submitAfterComposition) {
    submitAfterComposition = false;
    // 等待输入法的最终 input 事件更新 v-model，再发送并清空文本。
    void nextTick(submit);
  }
}

function handleKeydown(event: KeyboardEvent): void {
  // Safari 确认候选词的 Enter 可能已结束 composition，但仍带有 keyCode 229。
  if (isComposing.value || event.isComposing || event.keyCode === 229) {
    return;
  }
  if (editingMessage.value && event.key === 'Escape') {
    event.preventDefault();
    if (!editDisabled.value && !props.loading) {
      emit('finishQueuedEdit', editingMessage.value.id, false);
    }
    return;
  }
  if (skillMenuVisible.value) {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      moveSkillHighlight(event.key === 'ArrowDown' ? 1 : -1);
      return;
    }
    if ((event.key === 'Enter' || event.key === 'Tab') && matchedSkills.value.length) {
      event.preventDefault();
      selectSkill(matchedSkills.value[highlightedSkillIndex.value]);
      return;
    }
    if (event.key === ' ') {
      const exactSkill = matchedSkills.value.find(
        (skill) => skill.name.toLowerCase() === skillCommandQuery.value,
      );
      if (exactSkill) {
        event.preventDefault();
        selectSkill(exactSkill);
        return;
      }
    }
    if (event.key === 'Escape') {
      event.preventDefault();
      skillMenuDismissed.value = true;
      return;
    }
  }
  if (!editingMessage.value && event.key === 'Backspace' && selectedSkill.value && textarea.value?.selectionStart === 0) {
    event.preventDefault();
    removeSelectedSkill();
    return;
  }
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault();
    submit();
  }
}

/**
 * 根据方向移动 Skill 联想项的键盘焦点。
 */
function moveSkillHighlight(offset: number): void {
  const total = matchedSkills.value.length;
  if (!total) {
    return;
  }
  highlightedSkillIndex.value = (highlightedSkillIndex.value + offset + total) % total;
  void nextTick(() => {
    const list = skillCommandList.value;
    const item = list?.querySelector<HTMLElement>('.skill-command-item.active');
    if (!list || !item) {
      return;
    }
    // 只滚动候选列表，避免方向键选择带动外层聊天页面。
    if (item.offsetTop < list.scrollTop) {
      list.scrollTop = item.offsetTop;
    } else if (item.offsetTop + item.offsetHeight > list.scrollTop + list.clientHeight) {
      list.scrollTop = item.offsetTop + item.offsetHeight - list.clientHeight;
    }
  });
}

/**
 * 选中 Skill 后写入斜杠命令，发送时由 Agent 加载并执行该 Skill。
 */
function selectSkill(skill: AgentSkill): void {
  content.value = '';
  selectedSkill.value = skill;
  skillMenuDismissed.value = true;
  void nextTick(() => textarea.value?.focus());
}

/**
 * 一次移除当前选中的 Skill，并将焦点放回任务输入框。
 */
function removeSelectedSkill(): void {
  selectedSkill.value = null;
  void nextTick(() => textarea.value?.focus());
}

function chooseLocalFile(): void {
  if (editingMessage.value || !props.modelConfigured || props.loading || props.uploadingFiles) {
    return;
  }
  fileInput.value?.click();
}

function handleFileSelection(event: Event): void {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files || []);
  input.value = '';
  if (!files.length) {
    return;
  }
  emit('upload', files);
}

/**
 * 截图沿用附件上传和排队逻辑，普通文本粘贴继续交给输入框处理。
 */
function handlePaste(event: ClipboardEvent): void {
  if (editingMessage.value || !props.modelConfigured || props.loading || props.uploadingFiles) return;
  const files = Array.from(event.clipboardData?.items || [])
    .filter((item) => item.kind === 'file' && item.type.startsWith('image/'))
    .map((item) => item.getAsFile())
    .filter((file): file is File => file !== null);
  if (!files.length) return;
  event.preventDefault();
  emit('upload', files);
}

function formatSize(sizeBytes: number): string {
  if (sizeBytes < 1024) return `${sizeBytes} B`;
  if (sizeBytes < 1024 * 1024) return `${(sizeBytes / 1024).toFixed(1)} KB`;
  return `${(sizeBytes / 1024 / 1024).toFixed(1)} MB`;
}

</script>

<template>
  <div class="composer-wrap">
    <div v-if="!modelConfigured" class="model-setting-required">
      <span>配置个人模型后即可开始对话。</span>
      <el-button type="primary" link @click="emit('configureModel')">立即配置</el-button>
    </div>
    <section
      v-if="queuedMessages?.length"
      ref="queueElement"
      class="composer-queue"
      :class="{ 'is-stacked': queueCollapsed }"
      :aria-label="`待发送消息 · ${queuedMessages.length}`"
      @mouseleave="queueExpanded = false"
      @keydown.esc="queueExpanded = false"
    >
      <button
        v-if="queueCollapsible"
        type="button"
        class="composer-queue-toggle"
        :aria-expanded="queueExpanded"
        :title="queueCollapsed ? '展开待发送消息' : '收起待发送消息'"
        @click="queueExpanded = !queueExpanded"
      >
        <el-icon class="composer-queue-icon"><Clock /></el-icon>
        <span class="composer-queue-content">{{ queueCollapsed ? queuedMessages[0].content : '待发送消息' }}</span>
        <span class="composer-queue-count">{{ queuedMessages.length }} 条待发送</span>
        <el-icon><ArrowDown v-if="queueCollapsed" /><ArrowUp v-else /></el-icon>
      </button>
      <ol v-if="!queueCollapsed" class="composer-queue-list">
        <li v-for="message in queuedMessages" :key="message.id" class="composer-queue-item">
          <div class="composer-queue-row">
            <el-icon class="composer-queue-icon" :title="queuePaused ? '队列已暂停' : '等待发送'"><Clock /></el-icon>
            <span class="composer-queue-content" :title="message.content">{{ message.content }}</span>
            <span v-if="message.draft !== undefined" class="composer-queue-editing">编辑中</span>
            <span v-if="message.files.length" class="composer-queue-attachments" :title="message.files.map((file) => file.name).join('、')">
              <el-icon><Document /></el-icon>{{ message.files.length }}
            </span>
            <div class="composer-queue-actions">
              <button
                type="button"
                class="composer-steer-button"
                :disabled="interrupting || loading || message.draft !== undefined || message.removing"
                :title="interrupting ? '正在结束当前回答…' : sending ? '调整方向' : '立即发送'"
                @click="emit('steer', message.id)"
              >
                <el-icon><Right /></el-icon><span>{{ interrupting ? '请稍候' : sending ? '调整方向' : '立即发送' }}</span>
              </button>
              <button
                type="button"
                class="composer-queue-action"
                title="编辑待发送消息"
                :disabled="interrupting || loading || message.removing || Boolean(editingMessage)"
                @click="emit('editQueuedMessage', message.id)"
              ><el-icon><EditPen /></el-icon></button>
              <button
                type="button"
                class="composer-queue-action"
                title="删除待发送消息"
                :disabled="interrupting || loading || message.removing"
                @click="emit('removeQueuedMessage', message.id)"
              ><el-icon :class="{ 'is-loading': message.removing }"><Loading v-if="message.removing" /><Delete v-else /></el-icon></button>
            </div>
          </div>
        </li>
      </ol>
    </section>
    <div class="chat-composer" :class="{ focused: composerContent }">
      <div v-if="editingMessage" class="composer-edit-banner">
        <span>编辑待发送消息</span>
        <button type="button" :disabled="editDisabled || loading" @click="emit('finishQueuedEdit', editingMessage.id, false)">取消编辑</button>
      </div>
      <div v-if="skillMenuVisible" class="skill-command-menu">
        <div class="skill-command-title">
          <span>可用 Skill</span>
          <span>方向键选择 · Enter 确认</span>
        </div>
        <div ref="skillCommandList" class="skill-command-list" @mousedown.prevent>
          <div v-if="skillsLoading" class="skill-command-empty">正在加载 Skill…</div>
          <button
            v-for="(skill, index) in matchedSkills"
            v-else
            :key="skill.name"
            class="skill-command-item"
            :class="{ active: index === highlightedSkillIndex }"
            type="button"
            @mouseenter="highlightedSkillIndex = index"
            @mousedown.prevent="selectSkill(skill)"
          >
            <span class="skill-command-icon"><el-icon><MagicStick /></el-icon></span>
            <span class="skill-command-content">
              <strong>/{{ skill.name }}</strong>
              <small>{{ skill.description }}</small>
            </span>
          </button>
          <div v-if="!skillsLoading && !matchedSkills.length" class="skill-command-empty">没有匹配的 Skill</div>
        </div>
      </div>
      <div v-if="composerFiles.length || (!editingMessage && uploadingFiles)" class="composer-files">
        <div v-for="file in composerFiles" :key="file.id" class="composer-file-chip">
          <FileImagePreview v-if="isImageFile(file)" :file="file" compact />
          <el-icon v-else><Document /></el-icon>
          <span>
            <strong>{{ file.name }}</strong>
            <small>{{ formatSize(file.sizeBytes) }}</small>
          </span>
          <button v-if="!editingMessage" type="button" title="移除附件" @click="emit('removeFile', file.id)">
            <el-icon><Close /></el-icon>
          </button>
        </div>
        <div v-if="!editingMessage && uploadingFiles" class="composer-file-chip uploading">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span><strong>正在上传…</strong><small>完成后即可发送</small></span>
        </div>
      </div>
      <slot name="context" :disabled="Boolean(editingMessage)" />
      <div class="composer-input-area">
        <span v-if="selectedSkill && !editingMessage" class="selected-skill-chip">
          <el-icon><MagicStick /></el-icon>
          <strong>/{{ selectedSkill.name }}</strong>
          <button type="button" title="移除 Skill" @click="removeSelectedSkill">
            <el-icon><Close /></el-icon>
          </button>
        </span>
        <textarea
          ref="textarea"
          v-model="composerContent"
          rows="2"
          :disabled="!composerModelId || !modelConfigured || loading || editDisabled"
          :aria-label="editingMessage ? '编辑待发送消息内容' : '消息内容'"
          :placeholder="!modelConfigured ? '请先配置个人模型' : editingMessage ? '修改这条待发送消息…' : selectedSkill ? '描述要交给该 Skill 执行的任务…' : '描述任务，或输入 / 调用 Skill'"
          @input="skillMenuDismissed = false"
          @blur="skillMenuDismissed = true"
          @compositionstart="isComposing = true"
          @compositionend="handleCompositionEnd"
          @change="handleCompositionEnd"
          @keydown="handleKeydown"
          @paste="handlePaste"
        />
      </div>
      <small v-if="composerFiles.some(isImageFile)" class="composer-image-hint">
        {{ supportsImageInput(selectedModel) ? '当前模型将直接理解图片' : '当前模型通过工作区工具处理图片，可使用 OCR 提取文字' }}
      </small>
      <div class="composer-footer">
        <input
          ref="fileInput"
          class="composer-file-input"
          type="file"
          multiple
          @change="handleFileSelection"
        />
        <button
          class="composer-tool-button"
          type="button"
          title="上传会话文件"
          :disabled="Boolean(editingMessage) || !modelConfigured || loading || uploadingFiles"
          @click="chooseLocalFile"
        >
          <el-icon><Plus /></el-icon>
        </button>
        <slot name="tools" :disabled="Boolean(editingMessage)" />
        <el-select
          :model-value="composerModelId"
          class="model-select"
          placeholder="选择模型"
          :style="{ '--model-select-width': modelSelectWidth }"
          :disabled="Boolean(editingMessage) || !modelConfigured"
          @update:model-value="emit('update:modelId', $event)"
        >
          <el-option
            v-for="model in models"
            :key="model.id"
            :label="model.name || model.modelName"
            :value="model.id"
          >
            <span>{{ model.name || model.modelName }}</span>
            <el-tooltip
              v-if="supportsImageInput(model) || supportsImageOutput(model)"
              :content="`多模态模型：${[supportsImageInput(model) ? '图片理解' : '', supportsImageOutput(model) ? '图片生成' : ''].filter(Boolean).join('、')}`"
              placement="top"
              :show-after="250"
            >
              <el-icon class="model-capability-icon" aria-label="多模态模型" tabindex="0"><Picture /></el-icon>
            </el-tooltip>
          </el-option>
        </el-select>
        <span class="composer-hint">{{ editingMessage ? 'Enter 保存修改' : sending ? 'Enter 加入待发送' : 'Enter 发送' }} · Shift + Enter 换行</span>
        <button v-if="sending" class="send-button stop" type="button" title="停止" :disabled="interrupting" @click="emit('stop')">
          <el-icon><VideoPause /></el-icon>
        </button>
        <button class="send-button" type="button" :disabled="!canSend" :title="editingMessage ? '保存修改' : sending ? '加入待发送' : '发送'" @click="submit">
          <el-icon><Check v-if="editingMessage" /><Promotion v-else /></el-icon>
        </button>
      </div>
    </div>
    <p>请核对 Agent 返回的重要信息</p>
  </div>
</template>

<style scoped>
.chat-composer {
  container-name: composer;
  container-type: inline-size;
}

.composer-footer {
  flex-wrap: wrap;
}

@container composer (max-width: 620px) {
  .composer-hint { display: none; }
  .model-select { flex: 0 1 auto; max-width: 32%; }
}

.model-setting-required {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 8px 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
