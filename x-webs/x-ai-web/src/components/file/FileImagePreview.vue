<script setup lang="ts">
import { Download } from '@element-plus/icons-vue';
import type { ImageInstance } from 'element-plus';
import { ref, watch } from 'vue';

import { downloadConversationFile } from '@/api/conversation-file';
import type { ConversationFile } from '@/types/chat';

const props = defineProps<{ file: ConversationFile; compact?: boolean }>();
const emit = defineEmits<{ download: [] }>();
const image = ref<ImageInstance>();
const imageUrl = ref('');
const failed = ref(false);

watch(() => [props.file.sessionId, props.file.id], async (_, __, onCleanup) => {
  let disposed = false;
  let objectUrl = '';
  imageUrl.value = '';
  failed.value = false;
  onCleanup(() => {
    disposed = true;
    if (objectUrl) URL.revokeObjectURL(objectUrl);
  });
  try {
    // 通过现有鉴权请求取图，不把访问令牌拼进图片 URL。
    const blob = await downloadConversationFile(props.file.sessionId, props.file.id);
    if (disposed) return;
    objectUrl = URL.createObjectURL(blob);
    imageUrl.value = objectUrl;
  } catch {
    if (!disposed) failed.value = true;
  }
}, { immediate: true });
</script>

<template>
  <div class="file-image-preview" :class="{ compact, failed }">
    <el-image
      v-if="imageUrl && !failed"
      ref="image"
      :src="imageUrl"
      :alt="file.name"
      :preview-src-list="[imageUrl]"
      :aria-label="`预览图片：${file.name}`"
      role="button"
      tabindex="0"
      fit="contain"
      preview-teleported
      @error="failed = true"
      @keydown.enter.prevent="image?.showPreview()"
      @keydown.space.prevent="image?.showPreview()"
    >
      <template v-if="!compact" #viewer>
        <button
          class="image-download-button image-viewer-download"
          type="button"
          title="下载图片"
          :aria-label="`下载图片：${file.name}`"
          @click.stop="emit('download')"
        >
          <el-icon><Download /></el-icon>
        </button>
      </template>
    </el-image>
    <span v-else class="file-image-placeholder">{{ failed ? '预览失败，可下载查看' : '图片加载中…' }}</span>
    <button
      v-if="!compact"
      class="image-download-button image-preview-download"
      type="button"
      title="下载图片"
      :aria-label="`下载图片：${file.name}`"
      @click.stop="emit('download')"
    >
      <el-icon><Download /></el-icon>
    </button>
  </div>
</template>

<style scoped>
.file-image-preview {
  position: relative;
  display: grid;
  min-height: 100px;
  place-items: center;
  overflow: hidden;
  border: 1px solid var(--primary-border);
  border-radius: 13px;
  background: var(--primary-fill);
}

.image-download-button {
  position: absolute;
  display: grid;
  width: 34px;
  height: 34px;
  padding: 0;
  place-items: center;
  border: 1px solid var(--line-strong);
  border-radius: 10px;
  color: var(--text);
  background: var(--floating-panel-bg);
  box-shadow: 0 2px 8px var(--shadow);
  cursor: pointer;
  font-size: 17px;
}

.image-download-button:hover {
  color: var(--text-accent);
  background: var(--surface);
}

.image-download-button:focus-visible,
.file-image-preview :deep(.el-image:focus-visible) {
  outline: 2px solid var(--primary);
  outline-offset: -3px;
}

.image-preview-download {
  top: 10px;
  right: 10px;
  opacity: 0;
  pointer-events: none;
  transition: opacity 150ms ease;
}

.file-image-preview:hover .image-preview-download,
.file-image-preview:focus-within .image-preview-download,
.file-image-preview.failed .image-preview-download {
  opacity: 1;
  pointer-events: auto;
}

.image-viewer-download {
  top: 40px;
  right: 92px;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  font-size: 20px;
}

@media (hover: none) {
  .image-preview-download {
    opacity: 1;
    pointer-events: auto;
  }
}

.file-image-preview :deep(.el-image) {
  display: block;
  width: 100%;
  max-height: 360px;
}

.file-image-preview :deep(img) {
  max-height: 360px;
}

.file-image-preview.compact {
  width: 52px;
  height: 52px;
  min-height: 0;
  flex: 0 0 52px;
  border-radius: 7px;
}

.compact :deep(.el-image),
.compact :deep(img) {
  height: 52px;
}

.file-image-placeholder {
  padding: 8px;
  color: var(--text-muted);
  font-size: 12px;
  text-align: center;
}
</style>
