<script setup lang="ts">
import { ArrowDown, ArrowUp } from '@element-plus/icons-vue';
import { computed, ref, useId, watch } from 'vue';

import type { KnowledgeChunk } from '@/types/knowledge';

const props = defineProps<{ chunk: KnowledgeChunk; matched: boolean }>();
const contentId = `chunk-content-${useId()}`;
const previewElement = ref<HTMLElement>();
const expanded = ref(false);
const overflowing = ref(false);
const preview = computed(() => props.chunk.content.replace(/\s+/g, ' ').trim());

function measureContent(): void {
  const element = previewElement.value;
  overflowing.value = /[\r\n]/.test(props.chunk.content.trim())
    || Boolean(element && element.scrollWidth > element.clientWidth);
  if (!overflowing.value) expanded.value = false;
}

watch([() => props.chunk.content, () => props.matched], () => {
  expanded.value = false;
  measureContent();
}, { flush: 'post' });

watch(previewElement, (element, _, onCleanup) => {
  measureContent();
  if (!element || typeof ResizeObserver === 'undefined') return;
  const observer = new ResizeObserver(measureContent);
  observer.observe(element);
  onCleanup(() => observer.disconnect());
}, { flush: 'post' });
</script>

<template>
  <article class="chunk-card" :class="{ matched, expanded }">
    <component :is="overflowing ? 'button' : 'div'" class="chunk-row"
      :type="overflowing ? 'button' : undefined" :aria-expanded="overflowing ? expanded : undefined"
      :aria-controls="overflowing ? contentId : undefined"
      :aria-label="overflowing ? `${expanded ? '收起' : '展开'}切片 ${chunk.index} 全文` : undefined"
      @click="overflowing && (expanded = !expanded)">
      <strong class="chunk-index" :aria-label="`切片 ${chunk.index}`">{{ String(chunk.index).padStart(2, '0') }}</strong>
      <span ref="previewElement" class="chunk-preview">{{ preview }}</span>
      <span class="chunk-metadata">
        <span v-if="matched" class="match-label">命中</span>
        <span class="chunk-character-count">{{ chunk.characterCount }} 字符</span>
      </span>
      <el-icon v-if="overflowing" class="chunk-toggle"><ArrowUp v-if="expanded" /><ArrowDown v-else /></el-icon>
      <span v-else class="chunk-toggle-placeholder" aria-hidden="true" />
    </component>
    <div v-show="expanded" :id="contentId" class="chunk-content">
      <pre>{{ chunk.content }}</pre>
    </div>
  </article>
</template>

<style scoped>
.chunk-card { width: 100%; min-width: 0; border: 1px solid transparent; border-radius: 10px; background: transparent; }
.chunk-card + .chunk-card { margin-top: 2px; }
.chunk-card:has(button.chunk-row:hover), .chunk-card.expanded { border-color: var(--line); background: var(--surface); }
.chunk-card.matched { border-color: var(--primary-border); background: var(--primary-fill); }
.chunk-row { display: grid; grid-template-columns: 28px minmax(0, 1fr) auto 16px; align-items: center; gap: 12px; width: 100%; min-width: 0; min-height: 40px; padding: 6px 12px; border: 0; border-radius: 8px; color: var(--text-body); background: transparent; font: inherit; text-align: left; }
button.chunk-row { cursor: pointer; }
.chunk-index { display: grid; height: 24px; place-items: center; border-radius: 5px; color: var(--text-muted); background: var(--surface-control); font-family: "SFMono-Regular", Consolas, monospace; font-size: 11px; font-weight: 400; font-variant-numeric: tabular-nums; }
.expanded .chunk-index, .matched .chunk-index { color: var(--primary); background: var(--primary-fill-strong); }
.chunk-preview { display: block; min-width: 0; overflow: hidden; color: var(--text-soft); font-size: 13px; line-height: 24px; text-overflow: ellipsis; white-space: nowrap; }
.chunk-metadata { display: flex; align-items: center; gap: 12px; color: var(--text-muted); font-size: 12px; white-space: nowrap; font-variant-numeric: tabular-nums; }
.match-label { color: var(--primary); }
.chunk-toggle { color: var(--text-muted); font-size: 14px; }
.chunk-content { padding: 8px 12px 20px 40px; }
pre { margin: 0; padding-left: 12px; border-left: 1px solid var(--line); color: var(--text-body); font-family: inherit; font-size: 14px; line-height: 1.8; overflow-wrap: anywhere; white-space: pre-wrap; }
.chunk-row:focus-visible { outline: 2px solid var(--primary); outline-offset: -2px; }
@container reader (max-width: 520px) {
  .chunk-row { grid-template-columns: 28px minmax(0, 1fr) auto 16px; gap: 8px; min-height: 44px; padding-right: 8px; padding-left: 8px; }
  .chunk-character-count { display: none; }
  .chunk-content { padding: 0 12px 16px; }
}
</style>
