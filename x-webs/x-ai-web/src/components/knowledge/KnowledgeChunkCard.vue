<script setup lang="ts">
import { ArrowDown, ArrowUp } from '@element-plus/icons-vue';
import { nextTick, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue';

import type { KnowledgeChunk } from '@/types/knowledge';

const props = defineProps<{ chunk: KnowledgeChunk; matched: boolean }>();
const previewHeight = 96;
const contentId = `chunk-content-${useId()}`;
const contentElement = ref<HTMLElement>();
const expanded = ref(false);
const overflowing = ref(false);
let observer: ResizeObserver | undefined;

function measureContent(): void {
  overflowing.value = (contentElement.value?.scrollHeight || 0) > previewHeight;
  if (!overflowing.value) expanded.value = false;
}

watch(() => props.chunk.content, async () => {
  expanded.value = false;
  await nextTick();
  measureContent();
});

onMounted(() => {
  measureContent();
  if (typeof ResizeObserver !== 'undefined' && contentElement.value) {
    observer = new ResizeObserver(measureContent);
    observer.observe(contentElement.value);
  }
});

onBeforeUnmount(() => observer?.disconnect());
</script>

<template>
  <article class="chunk-card" :class="{ matched, expanded }" :style="{ '--chunk-preview-height': `${previewHeight}px` }">
    <header>
      <strong>切片 {{ chunk.index }}</strong>
      <span v-if="matched" class="match-label">检索命中</span>
      <span class="chunk-character-count">{{ chunk.characterCount }} 字符</span>
    </header>
    <div :id="contentId" class="chunk-content">
      <pre ref="contentElement">{{ chunk.content }}</pre>
    </div>
    <footer v-if="overflowing" class="chunk-footer">
      <button type="button" class="chunk-expand" :aria-expanded="expanded" :aria-controls="contentId"
        :aria-label="`${expanded ? '收起' : '展开'}切片 ${chunk.index} 全文`" @click="expanded = !expanded">
        {{ expanded ? '收起' : '展开全文' }}
        <el-icon><ArrowUp v-if="expanded" /><ArrowDown v-else /></el-icon>
      </button>
    </footer>
  </article>
</template>

<style scoped>
.chunk-card {
  margin-bottom: 12px;
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface);
}
.chunk-card:last-child { margin-bottom: 0; }
.chunk-card.matched { border-color: var(--primary-border-strong); box-shadow: inset 2px 0 0 var(--primary); }
header { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; color: var(--text-muted); font-size: 12px; font-variant-numeric: tabular-nums; }
strong { color: var(--text); font-size: 13px; font-weight: 500; }
.match-label { color: var(--primary); }
.chunk-character-count { margin-left: auto; }
.chunk-content { max-height: var(--chunk-preview-height); overflow: hidden; }
.expanded .chunk-content { max-height: none; }
pre { margin: 0; color: var(--text-body); font-family: inherit; font-size: 13px; line-height: 24px; overflow-wrap: anywhere; white-space: pre-wrap; }
.chunk-footer { display: flex; justify-content: flex-end; margin-top: 8px; }
.chunk-expand { display: inline-flex; align-items: center; gap: 8px; min-height: 28px; padding: 4px 8px; border: 0; border-radius: 6px; color: var(--text-muted); background: transparent; font: inherit; font-size: 12px; cursor: pointer; transition: color 160ms var(--ease), background-color 160ms var(--ease); }
.chunk-expand:hover { color: var(--text); background: var(--surface-hover); }
.chunk-expand:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
.chunk-expand .el-icon { font-size: 12px; }
</style>
