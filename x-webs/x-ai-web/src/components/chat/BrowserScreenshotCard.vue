<script setup lang="ts">
import { ArrowRight, Camera, CircleCheck, Close, Loading, Lock, Monitor } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { computed, ref } from 'vue';

import FileImagePreview from '@/components/file/FileImagePreview.vue';
import { useChatStore } from '@/stores/chat';
import type { BrowserScreenshotRequest } from '@/types/chat';

const props = defineProps<{ request: BrowserScreenshotRequest }>();
const chat = useChatStore();
const busy = computed(() => ['capturing', 'uploading', 'declining'].includes(props.request.status));
const completed = computed(() => ['received', 'declined', 'expired', 'cancelled'].includes(props.request.status));
const expanded = ref(false);
const labels = {
  pending: 'AI 想查看页面效果',
  capturing: '请选择预览所在的标签页或窗口',
  uploading: '正在将截图发送给 AI',
  declining: '正在跳过本次截图',
  received: '截图已发送给 AI',
  declined: '已跳过本次截图',
  expired: '截图请求已过期',
  cancelled: '截图请求已取消',
};

async function respond(accepted: boolean): Promise<void> {
  try {
    await chat.respondToScreenshot(props.request, accepted);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '截图回传失败');
  }
}
</script>

<template>
  <section class="screenshot-card" :class="[request.status, { compact: completed, expanded: completed && expanded }]" :aria-label="labels[request.status]">
    <button
      v-if="completed"
      class="screenshot-summary"
      type="button"
      :aria-expanded="expanded"
      :aria-controls="`screenshot-details-${request.requestId}`"
      @click="expanded = !expanded"
    >
      <CircleCheck v-if="request.status === 'received'" class="summary-status" />
      <Camera v-else class="summary-status" />
      <span role="status">{{ labels[request.status] }}</span>
      <small :title="request.previewFile.name">{{ request.previewFile.name }}</small>
      <ArrowRight class="summary-chevron" :class="{ expanded }" />
    </button>
    <header v-else class="screenshot-heading">
      <span class="screenshot-symbol">
        <el-icon v-if="busy" class="is-loading"><Loading /></el-icon>
        <Camera v-else />
      </span>
      <div class="screenshot-title">
        <strong role="status">{{ labels[request.status] }}</strong>
        <span v-if="request.status === 'declining'">正在通知 AI 继续处理</span>
        <span v-else-if="request.status === 'pending' || busy">截取一次画面，继续分析</span>
      </div>
      <span v-if="request.status === 'pending'" class="consent-badge">待授权</span>
    </header>
    <div v-if="!completed || expanded" :id="`screenshot-details-${request.requestId}`" class="screenshot-details">
      <p class="screenshot-reason">{{ request.reason }}</p>
      <el-tooltip content="在侧栏中查看页面" placement="top" :show-after="350">
        <button class="preview-link" type="button" aria-label="查看预览" @click="chat.openHtmlPreview(request.previewFile)">
          <Monitor />
          <span :title="request.previewFile.name">{{ request.previewFile.name }}</span>
          <small>预览</small>
          <ArrowRight class="preview-arrow" />
        </button>
      </el-tooltip>
      <template v-if="request.status === 'pending'">
        <div class="screenshot-permission">
          <Lock />
          <span>由你选择标签页或窗口 · 仅截图一次 · 随即停止共享</span>
        </div>
        <div class="screenshot-actions">
          <button class="decline-action" type="button" aria-label="拒绝截图" @click="respond(false)">
            <Close />暂不允许
          </button>
          <button class="consent-action" type="button" aria-label="同意截图并发送" @click="respond(true)">
            <Camera />允许截图并发送
          </button>
        </div>
      </template>
      <p v-else-if="request.status === 'capturing'" class="screenshot-progress">在浏览器弹窗中选择画面，完成后会自动发送</p>
      <p v-else-if="request.status === 'uploading'" class="screenshot-progress">截图完成，正在上传…</p>
      <p v-if="request.error" class="screenshot-error" role="alert">{{ request.error }}</p>
      <div v-if="request.files.length" class="screenshot-images">
        <FileImagePreview v-for="file in request.files" :key="file.id" :file="file" compact />
      </div>
    </div>
  </section>
</template>

<style scoped>
.screenshot-card {
  overflow: hidden;
  margin: 16px 0;
  padding: 20px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--surface);
}

.screenshot-card.pending {
  border-color: var(--primary-border);
}

.screenshot-card.compact {
  margin: 12px 0;
  padding: 0;
  border-color: transparent;
  border-radius: 8px;
  background: transparent;
  box-shadow: none;
}

.screenshot-card.compact.expanded {
  border-color: var(--line);
  background: var(--surface);
}

.screenshot-summary {
  display: flex;
  width: 100%;
  min-height: 40px;
  padding: 8px 12px;
  align-items: center;
  gap: 8px;
  border: 0;
  border-radius: 8px;
  color: var(--text-soft);
  background: transparent;
  cursor: pointer;
  font-size: 12px;
  text-align: left;
  transition: background-color 160ms var(--ease);
}

.screenshot-summary:hover {
  background: var(--surface-control-hover);
}

.screenshot-summary > span {
  flex-shrink: 0;
}

.screenshot-summary > small {
  overflow: hidden;
  min-width: 0;
  margin-left: auto;
  color: var(--text-muted);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.screenshot-summary > svg {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}

.received .summary-status {
  color: var(--success);
}

.screenshot-summary > .summary-chevron {
  width: 16px;
  height: 16px;
  color: var(--text-muted);
  transition: transform 160ms var(--ease);
}

.summary-chevron.expanded {
  transform: rotate(90deg);
}

.compact .screenshot-details {
  padding: 0 16px 16px;
  border-top: 1px solid var(--line-faint);
}

@media (prefers-reduced-motion: reduce) {
  .summary-chevron {
    transition: none;
  }
}

.screenshot-heading {
  display: flex;
  align-items: center;
  gap: 12px;
}

.screenshot-symbol {
  display: grid;
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  place-items: center;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  color: var(--text-soft);
  background: var(--surface-subtle);
}

.received .screenshot-symbol {
  border-color: var(--success-border);
  color: var(--success);
  background: var(--success-soft);
}

.screenshot-symbol svg {
  width: 16px;
  height: 16px;
}

.screenshot-title {
  display: grid;
  min-width: 0;
  flex: 1;
  gap: 4px;
}

.screenshot-title strong {
  color: var(--text-heading);
  font-size: 13px;
  font-weight: 500;
  line-height: 1.5;
}

.screenshot-title > span {
  color: var(--text-muted);
  font-size: 11px;
}

.consent-badge {
  flex-shrink: 0;
  padding: 4px 0 4px 8px;
  color: var(--text-accent);
  font-size: 11px;
}

.screenshot-reason {
  margin: 16px 0 12px;
  color: var(--text-soft);
  font-size: 13px;
  line-height: 1.75;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.preview-link {
  display: flex;
  width: 100%;
  min-width: 0;
  margin-top: 12px;
  padding: 12px;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  color: var(--text-soft);
  background: var(--surface-subtle);
  cursor: pointer;
  text-align: left;
  transition: background-color 160ms var(--ease), border-color 160ms var(--ease);
}

.preview-link:hover {
  border-color: var(--primary-border);
  background: var(--primary-fill);
}

.preview-link > span {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-link > small {
  color: var(--text-muted);
  font-size: 11px;
}

.preview-link svg {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}

.preview-link .preview-arrow {
  width: 16px;
  height: 16px;
  color: var(--text-muted);
}

.screenshot-permission {
  display: flex;
  margin: 12px 0 16px;
  align-items: flex-start;
  gap: 8px;
  color: var(--text-muted);
  font-size: 11px;
  line-height: 1.7;
}

.screenshot-permission svg {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  margin-top: 2px;
}

.screenshot-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.screenshot-actions button {
  display: inline-flex;
  min-height: 36px;
  padding: 8px 12px;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 1px solid transparent;
  border-radius: 8px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 500;
  transition: background-color 160ms var(--ease), border-color 160ms var(--ease), color 160ms var(--ease);
}

.screenshot-actions svg {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}

.decline-action {
  color: var(--text-soft);
  background: var(--surface-control);
}

.decline-action:hover {
  color: var(--text);
  background: var(--surface-control-hover);
}

.screenshot-actions .consent-action {
  border-color: var(--primary-action-border);
  color: var(--primary-action-text);
  background: var(--primary-action-bg);
}

.screenshot-actions .consent-action:hover {
  border-color: var(--primary-action-hover-border);
  color: var(--primary-action-hover-text);
  background: var(--primary-action-hover-bg);
}

button:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 2px;
}

.screenshot-progress,
.screenshot-error {
  margin: 12px 0 0;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.screenshot-error {
  color: var(--danger-text);
}

.screenshot-images {
  margin-top: 12px;
}

@media (max-width: 480px) {
  .screenshot-card {
    padding: 16px;
  }

  .screenshot-actions button {
    flex: 1;
    padding-right: 8px;
    padding-left: 8px;
  }
}
</style>
