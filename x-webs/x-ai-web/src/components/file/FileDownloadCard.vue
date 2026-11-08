<script setup lang="ts">
import { Document, Download, Monitor, VideoPlay } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';

import { downloadConversationFile } from '@/api/conversation-file';
import FileImagePreview from '@/components/file/FileImagePreview.vue';
import { CONVERSATION_FILE_KIND } from '@/types/chat';
import type { ConversationFile } from '@/types/chat';
import { isHtmlFile, isImageFile } from '@/utils/conversation-file';

const props = defineProps<{
  file: ConversationFile;
  description?: string;
  previewable?: boolean;
}>();
const emit = defineEmits<{ preview: [file: ConversationFile] }>();

async function download(): Promise<void> {
  try {
    const blob = await downloadConversationFile(props.file.sessionId, props.file.id);
    const link = window.document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = props.file.name;
    link.click();
    URL.revokeObjectURL(link.href);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '文件下载失败');
  }
}

function formatSize(sizeBytes: number): string {
  if (sizeBytes < 1024) return `${sizeBytes} B`;
  if (sizeBytes < 1024 * 1024) return `${(sizeBytes / 1024).toFixed(1)} KB`;
  return `${(sizeBytes / 1024 / 1024).toFixed(1)} MB`;
}

function defaultDescription(): string {
  return props.file.kind === CONVERSATION_FILE_KIND.OUTPUT ? 'AI 生成' : '会话附件';
}
</script>

<template>
  <div class="conversation-file-card">
    <FileImagePreview v-if="isImageFile(file)" :file="file" @download="download" />
    <div v-else-if="previewable && isHtmlFile(file)" class="html-file-card">
      <el-tooltip content="在侧栏中运行预览" placement="top" :show-after="350">
        <button class="html-file-preview" type="button" :aria-label="`预览 ${file.name}`" @click="emit('preview', file)">
          <span class="html-file-icon"><Monitor /></span>
          <span class="file-download-main">
            <strong>{{ file.name }}</strong>
            <small>HTML 页面 · {{ formatSize(file.sizeBytes) }}</small>
          </span>
          <VideoPlay class="html-run-icon" />
        </button>
      </el-tooltip>
      <el-tooltip content="下载 HTML" placement="top" :show-after="350">
        <button class="html-download-action" type="button" :aria-label="`下载 ${file.name}`" @click="download">
          <Download />
        </button>
      </el-tooltip>
    </div>
    <button v-else class="file-download-card" type="button" @click="download">
      <span class="file-download-icon"><el-icon><Document /></el-icon></span>
      <span class="file-download-main">
        <strong>{{ file.name }}</strong>
        <small>{{ description || defaultDescription() }} · {{ formatSize(file.sizeBytes) }}</small>
      </span>
      <el-icon class="file-download-action"><Download /></el-icon>
    </button>
  </div>
</template>

<style scoped>
.conversation-file-card {
  display: grid;
  min-width: 0;
  gap: 6px;
}

.html-file-card {
  display: flex;
  min-width: 0;
  padding-right: 10px;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--line);
  border-radius: 13px;
  background: var(--surface);
  transition: border-color 150ms, box-shadow 150ms;
}

.html-file-card:hover {
  border-color: var(--primary-border-medium);
  box-shadow: 0 3px 12px var(--surface-control);
}

.html-file-preview {
  display: flex;
  flex: 1;
  min-width: 0;
  padding: 12px;
  align-items: center;
  gap: 11px;
  border: 0;
  border-radius: 12px;
  color: var(--text);
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.html-file-icon {
  display: grid;
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  place-items: center;
  border: 1px solid var(--primary-border);
  border-radius: 11px;
  color: var(--primary-icon);
  background: var(--primary-fill);
}

.html-file-icon svg {
  width: 20px;
  height: 20px;
}

.html-file-preview .file-download-main {
  flex: 1;
}

.html-run-icon {
  width: 21px;
  height: 21px;
  flex-shrink: 0;
  color: var(--primary-icon);
}

.html-download-action {
  display: grid;
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  place-items: center;
  border: 0;
  border-radius: 8px;
  color: var(--text-muted);
  background: transparent;
  cursor: pointer;
}

.html-download-action:hover {
  color: var(--text);
  background: var(--surface-control-hover);
}

.html-download-action svg {
  width: 17px;
  height: 17px;
}

.html-file-card button:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 2px;
}
</style>
