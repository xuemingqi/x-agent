<script setup lang="ts">
import { Close, Loading, Monitor, RefreshRight, Warning } from '@element-plus/icons-vue';
import { ref, watch } from 'vue';

import { downloadConversationFile } from '@/api/conversation-file';
import { BROWSER_PREVIEW_CONFIG } from '@/config/browser-preview';
import type { ConversationFile } from '@/types/chat';
import { buildHtmlPreview } from '@/utils/html-preview';

const props = defineProps<{ file: ConversationFile }>();
const emit = defineEmits<{ close: [] }>();
const documentHtml = ref('');
const error = ref('');
const revision = ref(0);
const loaded = ref(false);

watch([() => props.file.sessionId, () => props.file.id, revision], async (_, __, onCleanup) => {
  let disposed = false;
  onCleanup(() => { disposed = true; });
  documentHtml.value = '';
  error.value = '';
  loaded.value = false;
  try {
    if (props.file.sizeBytes > BROWSER_PREVIEW_CONFIG.maxHtmlSizeBytes) throw new Error('HTML 文件过大，请下载查看');
    const blob = await downloadConversationFile(props.file.sessionId, props.file.id);
    if (blob.size > BROWSER_PREVIEW_CONFIG.maxHtmlSizeBytes) throw new Error('HTML 文件过大，请下载查看');
    const html = await blob.text();
    if (!disposed) documentHtml.value = buildHtmlPreview(html);
  } catch (cause) {
    if (!disposed) error.value = cause instanceof Error ? cause.message : '预览加载失败';
  }
}, { immediate: true });
</script>

<template>
  <aside class="html-preview-panel" aria-label="HTML 运行预览">
    <header class="preview-toolbar">
      <span class="preview-symbol"><Monitor /></span>
      <div class="preview-heading">
        <strong :title="file.name">{{ file.name }}</strong>
        <span>页面预览</span>
      </div>
      <el-tooltip content="重新运行" placement="bottom" :show-after="250">
        <button class="toolbar-action" type="button" aria-label="重新运行预览" @click="revision++">
          <RefreshRight />
        </button>
      </el-tooltip>
      <el-tooltip content="收起预览" placement="bottom" :show-after="250">
        <button class="toolbar-action" type="button" aria-label="关闭预览" @click="emit('close')">
          <Close />
        </button>
      </el-tooltip>
    </header>
    <div class="preview-canvas">
      <iframe
        v-if="documentHtml"
        :key="`${file.id}-${revision}`"
        :srcdoc="documentHtml"
        :title="`HTML 预览：${file.name}`"
        sandbox="allow-scripts"
        referrerpolicy="no-referrer"
        allow="camera 'none'; microphone 'none'; geolocation 'none'; display-capture 'none'; clipboard-read 'none'; clipboard-write 'none'"
        @load="loaded = true"
      />
      <div v-else class="preview-placeholder" role="status">
        <Warning v-if="error" />
        <el-icon v-else class="is-loading"><Loading /></el-icon>
        <p>{{ error || '正在准备预览…' }}</p>
        <button v-if="error" class="retry-action" type="button" @click="revision++">重新加载</button>
      </div>
    </div>
    <footer class="preview-footer">
      <span>HTML · 仅加载页面内资源</span>
      <span class="preview-status" :class="{ ready: loaded }" role="status">
        <i />{{ error ? '加载失败' : loaded ? '已就绪' : '加载中' }}
      </span>
    </footer>
  </aside>
</template>

<style scoped>
.html-preview-panel {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
  border-left: 1px solid var(--line-soft);
  background: var(--surface);
}

.preview-toolbar {
  display: flex;
  min-height: 66px;
  flex-shrink: 0;
  padding: 12px 16px;
  align-items: center;
  gap: 6px;
}

.preview-symbol {
  display: grid;
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  margin-right: 4px;
  place-items: center;
  border: 1px solid var(--line-soft);
  border-radius: 10px;
  color: var(--text-soft);
  background: var(--surface-subtle);
}

.preview-symbol svg {
  width: 18px;
  height: 18px;
}

.preview-heading {
  display: grid;
  flex: 1;
  min-width: 0;
  gap: 4px;
}

.preview-heading strong {
  overflow: hidden;
  color: var(--text-heading);
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-heading span {
  color: var(--text-muted);
  font-size: 11px;
}

.toolbar-action {
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
  transition: background 150ms, color 150ms;
}

.toolbar-action:hover {
  color: var(--text);
  background: var(--surface-control-hover);
}

.toolbar-action svg {
  width: 17px;
  height: 17px;
}

button:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 2px;
}

.preview-canvas {
  display: flex;
  flex: 1;
  min-height: 0;
  padding: 0 12px;
}

iframe,
.preview-placeholder {
  flex: 1;
  width: 100%;
  min-width: 0;
  border: 1px solid var(--line);
  border-radius: 10px;
}

iframe {
  height: 100%;
  background: white;
}

.preview-placeholder {
  display: flex;
  padding: 24px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  color: var(--text-muted);
  background: var(--surface-subtle);
  font-size: 12px;
  text-align: center;
}

.preview-placeholder svg {
  width: 24px;
  height: 24px;
}

.retry-action {
  padding: 6px 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
  color: var(--text);
  background: var(--surface);
  cursor: pointer;
}

.preview-footer {
  display: flex;
  min-height: 34px;
  flex-shrink: 0;
  padding: 8px 16px;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  color: var(--text-muted);
  font-size: 10px;
}

.preview-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  white-space: nowrap;
}

.preview-status i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}

.preview-status.ready {
  color: var(--success-text);
}

@media (max-width: 900px) {
  .html-preview-panel {
    border-top: 1px solid var(--line-soft);
    border-left: 0;
  }
}
</style>
