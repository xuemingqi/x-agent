<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { computed, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import ChatComposer from '@/components/chat/ChatComposer.vue';
import ChatKnowledgeSources from '@/components/chat/ChatKnowledgeSources.vue';
import ChatTimeline from '@/components/chat/ChatTimeline.vue';
import FileHtmlPreview from '@/components/file/FileHtmlPreview.vue';
import { useChatStore } from '@/stores/chat';
import { useModelSettingStore } from '@/stores/model-setting';
import { consumeApplicationChatRequest } from '@/utils/app-chat';

const route = useRoute();
const router = useRouter();
const chat = useChatStore();
const modelSetting = useModelSettingStore();
const sessionId = computed(() => String(route.params.sessionId || ''));
const conversationTitle = computed(() => chat.conversations.find((item) => item.sessionId === chat.currentSessionId)?.title
  || (chat.currentSessionId ? '当前对话' : '新对话'));
const previewHint = computed(() => chat.previewFile ? (chat.previewOpen ? '收起预览侧栏' : '打开预览侧栏') : '生成 HTML 后可在这里预览');
const awaitingScreenshot = computed(() => chat.messages.some((message) => message.screenshots?.some((request) => request.status === 'pending')));
const selectedKnowledgeBases = computed(() => chat.knowledgeBases
  .filter((base) => !base.isDefault && chat.selectedKnowledgeBaseIds.includes(base.id)));
const commonKnowledgeName = computed(() => chat.knowledgeBases.find((base) => base.isDefault)?.name || '公共知识库');

async function updateKnowledgeBases(ids: string[]): Promise<void> {
  try {
    await chat.selectKnowledgeBases(ids);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '关联知识库失败');
  }
}

async function refreshKnowledgeBases(): Promise<void> {
  try {
    await chat.loadKnowledgeBases();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '知识库加载失败');
  }
}

function togglePreview(): void {
  if (chat.previewOpen) chat.closeHtmlPreview();
  else if (chat.previewFile) chat.openHtmlPreview(chat.previewFile);
}

onMounted(loadRouteConversation);
watch(sessionId, loadRouteConversation);
watch(() => route.query.appRequest, (request) => {
  if (typeof request === 'string') void loadRouteConversation();
});
watch(() => modelSetting.configured, (configured) => {
  if (configured && typeof route.query.appRequest === 'string') void loadRouteConversation();
});

let routeLoadSequence = 0;

async function loadRouteConversation(): Promise<void> {
  const sequence = ++routeLoadSequence;
  try {
    await chat.initialize();
    if (sequence !== routeLoadSequence) return;
    if (!sessionId.value) {
      chat.closeConversation();
    } else if (sessionId.value !== chat.currentSessionId) {
      await chat.openConversation(sessionId.value);
    }
    if (sequence !== routeLoadSequence) return;
    const token = route.query.appRequest;
    if (typeof token !== 'string') return;
    if (!modelSetting.configured) {
      modelSetting.openDialog();
      return;
    }
    const prompt = consumeApplicationChatRequest(token);
    const query = { ...route.query };
    delete query.appRequest;
    await router.replace({ path: route.path, query });
    if (prompt) await send(prompt);
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
          :loading="chat.loading || chat.savingKnowledgeBases || chat.creatingConversation"
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
        >
          <template #context="{ disabled }">
            <div class="composer-knowledge-context" aria-label="已关联知识库">
              <span class="common-knowledge-source" :title="`${commonKnowledgeName}：所有会话自动检索`">{{ commonKnowledgeName }} · 自动关联</span>
              <span v-for="base in selectedKnowledgeBases" :key="base.id" class="selected-knowledge-source">
                <span :title="base.name">{{ base.name }}</span>
                <button type="button" :aria-label="`移除知识库 ${base.name}`" :disabled="chat.knowledgeSelectionDisabled || disabled"
                  @click="updateKnowledgeBases(chat.selectedKnowledgeBaseIds.filter((id) => id !== base.id))">×</button>
              </span>
              <span v-if="chat.savingKnowledgeBases" class="knowledge-save-status" role="status">正在保存…</span>
            </div>
          </template>
          <template #tools="{ disabled }">
            <ChatKnowledgeSources :key="chat.currentSessionId || 'draft'" :knowledge-bases="chat.knowledgeBases"
              :selected-ids="chat.selectedKnowledgeBaseIds" :disabled="chat.knowledgeSelectionDisabled || disabled"
              :loading="chat.loadingKnowledgeBases" :saving="chat.savingKnowledgeBases"
              @change="updateKnowledgeBases" @reload="refreshKnowledgeBases" />
          </template>
        </ChatComposer>
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

.composer-knowledge-context {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 8px;
  max-height: 76px;
  margin: 0 0 12px;
  overflow-y: auto;
  font-size: 12px;
}

.common-knowledge-source {
  padding: 4px 0;
  color: var(--text-muted);
  line-height: 1.5;
}

.selected-knowledge-source {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 220px;
  padding: 2px 4px 2px 8px;
  border-radius: 6px;
  color: var(--text-soft);
  background: var(--surface-control);
}

.selected-knowledge-source > span {
  overflow: hidden;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.selected-knowledge-source button {
  display: grid;
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  padding: 0;
  place-items: center;
  border: 0;
  border-radius: 4px;
  color: var(--text-muted);
  background: transparent;
  font-size: 16px;
  cursor: pointer;
}

.selected-knowledge-source button:hover:not(:disabled) {
  color: var(--text);
  background: var(--surface-control-hover);
}

.selected-knowledge-source button:disabled {
  cursor: default;
  opacity: 0.5;
}

.knowledge-save-status {
  color: var(--text-muted);
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
