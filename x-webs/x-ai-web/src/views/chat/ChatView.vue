<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { computed, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import ChatComposer from '@/components/chat/ChatComposer.vue';
import ChatTimeline from '@/components/chat/ChatTimeline.vue';
import FileHtmlPreview from '@/components/file/FileHtmlPreview.vue';
import { useChatStore } from '@/stores/chat';
import { useModelSettingStore } from '@/stores/model-setting';

const route = useRoute();
const router = useRouter();
const chat = useChatStore();
const modelSetting = useModelSettingStore();
const sessionId = computed(() => String(route.params.sessionId || ''));
const conversationTitle = computed(() => chat.conversations.find((item) => item.sessionId === chat.currentSessionId)?.title
  || (chat.currentSessionId ? '当前对话' : '新对话'));
const previewHint = computed(() => chat.previewFile ? (chat.previewOpen ? '收起预览侧栏' : '打开预览侧栏') : '生成 HTML 后可在这里预览');
const awaitingScreenshot = computed(() => chat.messages.some((message) => message.screenshots?.some((request) => request.status === 'pending')));

function togglePreview(): void {
  if (chat.previewOpen) chat.closeHtmlPreview();
  else if (chat.previewFile) chat.openHtmlPreview(chat.previewFile);
}

onMounted(loadRouteConversation);
watch(sessionId, loadRouteConversation);

async function loadRouteConversation(): Promise<void> {
  try {
    await chat.initialize();
    if (!sessionId.value) {
      chat.closeConversation();
      return;
    }
    if (sessionId.value !== chat.currentSessionId) {
      await chat.openConversation(sessionId.value);
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载会话失败');
  }
}

async function updateModel(modelId: string): Promise<void> {
  try {
    await chat.selectModel(modelId);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '切换模型失败');
  }
}

async function send(content: string): Promise<void> {
  try {
    if (!chat.currentSessionId) {
      const conversation = await chat.createConversation();
      await router.replace(`/chat/${conversation.sessionId}`);
    }
    await chat.send(content);
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      return;
    }
    ElMessage.error(error instanceof Error ? error.message : '发送失败');
  }
}

async function upload(files: File[]): Promise<void> {
  try {
    if (!chat.currentSessionId) {
      const conversation = await chat.createConversation();
      await router.replace(`/chat/${conversation.sessionId}`);
    }
    await chat.uploadFiles(files);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '文件上传失败');
  }
}

async function removeFile(fileId: string): Promise<void> {
  try {
    await chat.removePendingFile(fileId);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '移除附件失败');
  }
}

async function stop(): Promise<void> {
  try {
    await chat.stop();
  } catch (error) {
    ElMessage.warning(error instanceof Error ? error.message : '停止请求未完成');
  }
}

async function steer(messageId: string): Promise<void> {
  try {
    await chat.steer(messageId);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '调整方向失败');
  }
}

async function finishQueuedEdit(messageId: string, save: boolean): Promise<void> {
  try {
    await chat.finishQueuedEdit(messageId, save);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '更新待发送消息失败');
  }
}

async function removeQueuedMessage(messageId: string): Promise<void> {
  try {
    await chat.removeQueuedMessage(messageId);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除待发送消息失败');
  }
}
</script>

<template>
  <div class="chat-workspace">
    <header class="workspace-toolbar">
      <span class="workspace-title">{{ conversationTitle }}</span>
      <el-tooltip :content="previewHint" placement="bottom-end" :show-after="250">
        <button
          class="preview-toggle"
          :class="{ active: chat.previewOpen, available: chat.previewFile }"
          type="button"
          :aria-label="previewHint"
          :aria-pressed="chat.previewOpen"
          :aria-disabled="!chat.previewFile"
          aria-controls="html-preview"
          @click="togglePreview"
        >
          <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <rect x="3" y="4" width="18" height="16" rx="3" />
            <path d="M15 4v16" />
            <path class="panel-fill" d="M16 6h2a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1h-2z" />
          </svg>
          <span v-if="awaitingScreenshot" class="preview-notification" />
        </button>
      </el-tooltip>
    </header>
    <div class="workspace-content" :class="{ 'with-preview': chat.previewFile && chat.previewOpen }">
      <section class="chat-page">
        <div v-if="!chat.messages.length && !chat.loading" class="chat-hero">
          <div class="workspace-mark agent-mark" aria-hidden="true" />
          <p>X Agent 工作台</p>
          <h1>开始一项新任务</h1>
          <span>描述目标、上传资料，或输入 / 调用 Skill</span>
        </div>
        <ChatTimeline
          v-else
          :messages="chat.messages"
          :loading="chat.loading"
          @preview="chat.openHtmlPreview"
        />
        <ChatComposer
          :models="chat.models"
          :model-id="chat.selectedModelId"
          :model-configured="modelSetting.configured"
          :sending="chat.sending"
          :files="chat.pendingFiles"
          :uploading-files="chat.uploadingFiles"
          :queued-messages="chat.queuedMessages"
          :interrupting="chat.interrupting"
          :queue-paused="chat.queuePaused"
          :loading="chat.loading"
          @send="send"
          @stop="stop"
          @steer="steer"
          @edit-queued-message="chat.editQueuedMessage"
          @update-queued-draft="chat.updateQueuedDraft"
          @finish-queued-edit="finishQueuedEdit"
          @remove-queued-message="removeQueuedMessage"
          @upload="upload"
          @remove-file="removeFile"
          @configure-model="modelSetting.openDialog"
          @update:model-id="updateModel"
        />
      </section>
      <FileHtmlPreview
        v-if="chat.previewFile"
        v-show="chat.previewOpen"
        id="html-preview"
        :key="chat.previewFile.sessionId"
        :file="chat.previewFile"
        @close="chat.closeHtmlPreview"
      />
    </div>
  </div>
</template>

<style scoped>
.chat-workspace {
  display: flex;
  height: 100%;
  min-width: 0;
  flex-direction: column;
}

.workspace-toolbar {
  display: flex;
  height: 56px;
  flex: 0 0 56px;
  padding: 0 24px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid transparent;
}

.workspace-title {
  overflow: hidden;
  color: var(--text-soft);
  font-size: 13px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-toggle {
  position: relative;
  display: grid;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  place-items: center;
  border: 1px solid transparent;
  border-radius: 8px;
  color: var(--text-muted);
  background: transparent;
  cursor: pointer;
  transition: background-color 160ms var(--ease), color 160ms var(--ease), border-color 160ms var(--ease);
}

.preview-toggle.available:hover {
  color: var(--text);
  background: var(--surface-control-hover);
}

.preview-toggle.active {
  border-color: var(--primary-border);
  color: var(--primary-icon);
  background: var(--primary-fill);
}

.preview-toggle[aria-disabled='true'] {
  color: var(--text-faint);
  cursor: default;
}

.preview-toggle:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 3px;
}

.preview-toggle svg {
  width: 18px;
  height: 18px;
  stroke: currentColor;
  stroke-width: 1.5;
}

.panel-fill {
  fill: currentColor;
  stroke: none;
  opacity: 0.18;
}

.preview-notification {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--primary);
  box-shadow: 0 0 0 2px var(--bg);
}

.workspace-content {
  display: grid;
  flex: 1;
  min-height: 0;
  min-width: 0;
}

.workspace-content.with-preview {
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
}

.chat-page {
  min-width: 0;
}

.chat-page :deep(.chat-timeline) {
  padding-top: 28px;
}

@container workspace (max-width: 800px) {
  .workspace-content.with-preview {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: minmax(350px, 1fr) minmax(260px, 36%);
    overflow: auto;
  }
}

@media (max-width: 760px) {
  .workspace-toolbar {
    height: 66px;
    flex-basis: 66px;
    padding-right: 16px;
    padding-left: 66px;
  }
}
</style>
