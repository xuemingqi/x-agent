<script setup lang="ts">
import { ArrowLeft, Delete, Document, Download, RefreshRight } from '@element-plus/icons-vue';
import { computed, nextTick, onMounted, ref, watch } from 'vue';

import {
  downloadKnowledgeDocument,
  getKnowledgeDocumentPreview,
  listKnowledgeDocumentChunks,
} from '@/api/knowledge';
import KnowledgeChunkCard from '@/components/knowledge/KnowledgeChunkCard.vue';
import type { KnowledgeChunkPage, KnowledgeDocument } from '@/types/knowledge';
import { renderMarkdown } from '@/utils/markdown';

const props = defineProps<{
  document: KnowledgeDocument;
  initialTab: 'preview' | 'chunks';
  initialChunkIndex?: number;
  reindexing?: boolean;
}>();
const emit = defineEmits<{ close: []; download: []; reindex: []; delete: [] }>();
const readerPanel = ref<HTMLElement>();
const readerScroll = ref<HTMLElement>();

const activeTab = ref(props.initialTab);
const content = ref('');
const loadingPreview = ref(false);
const previewError = ref('');
const previewRevision = ref(0);
const pageSize = 20;
const page = ref(Math.floor(((props.initialChunkIndex || 1) - 1) / pageSize) + 1);
const chunks = ref<KnowledgeChunkPage | null>(null);
const loadingChunks = ref(false);
const chunksError = ref('');
const chunksRevision = ref(0);
const showOriginal = ref(false);
const originalUrl = ref('');
const loadingOriginal = ref(false);
const originalError = ref('');
const isPdf = computed(() => props.document.name.toLowerCase().endsWith('.pdf'));
const isMarkdown = computed(() => /\.(md|markdown)$/i.test(props.document.name));
const renderedContent = computed(() => isMarkdown.value ? renderMarkdown(content.value) : '');

watch([() => props.document, previewRevision], async (_, __, onCleanup) => {
  let disposed = false;
  onCleanup(() => { disposed = true; });
  content.value = '';
  previewError.value = '';
  loadingPreview.value = true;
  try {
    const preview = await getKnowledgeDocumentPreview(props.document.knowledgeBaseId, props.document.path);
    if (!disposed) content.value = preview.content;
  } catch (error) {
    if (!disposed) previewError.value = error instanceof Error ? error.message : '正文加载失败';
  } finally {
    if (!disposed) loadingPreview.value = false;
  }
}, { immediate: true });

watch([() => props.document, activeTab, page, chunksRevision], async (_, __, onCleanup) => {
  let disposed = false;
  onCleanup(() => { disposed = true; });
  if (activeTab.value !== 'chunks') return;
  chunks.value = null;
  chunksError.value = '';
  loadingChunks.value = true;
  try {
    const result = await listKnowledgeDocumentChunks(props.document.knowledgeBaseId, props.document.path, page.value, pageSize);
    if (!disposed) {
      chunks.value = result;
      await nextTick();
      const scrollRegion = readerScroll.value;
      if (scrollRegion) {
        const matched = scrollRegion.querySelector<HTMLElement>('.chunk-card.matched');
        scrollRegion.scrollTop = matched ? Math.max(0, scrollRegion.scrollTop
          + matched.getBoundingClientRect().top - scrollRegion.getBoundingClientRect().top - 16) : 0;
      }
    }
  } catch (error) {
    if (!disposed) chunksError.value = error instanceof Error ? error.message : '切片加载失败';
  } finally {
    if (!disposed) loadingChunks.value = false;
  }
}, { immediate: true });

watch([() => props.document, showOriginal], async (_, __, onCleanup) => {
  let disposed = false;
  let objectUrl = '';
  originalUrl.value = '';
  originalError.value = '';
  onCleanup(() => {
    disposed = true;
    if (objectUrl) URL.revokeObjectURL(objectUrl);
  });
  loadingOriginal.value = false;
  if (!showOriginal.value || !isPdf.value) return;
  loadingOriginal.value = true;
  try {
    const blob = await downloadKnowledgeDocument(props.document.knowledgeBaseId, props.document.path);
    if (disposed) return;
    objectUrl = URL.createObjectURL(new Blob([blob], { type: 'application/pdf' }));
    originalUrl.value = objectUrl;
  } catch (error) {
    if (!disposed) originalError.value = error instanceof Error ? error.message : 'PDF 加载失败，可切换正文预览';
  } finally {
    if (!disposed) loadingOriginal.value = false;
  }
});
function formattedSize(size: number): string {
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

function formattedTime(value: string): string {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—';
}

onMounted(() => readerPanel.value?.focus({ preventScroll: true }));
</script>

<template>
  <section ref="readerPanel" class="document-reader" aria-label="文档阅读区" tabindex="-1">
    <header class="reader-header">
      <div class="reader-heading">
        <button type="button" class="reader-back" title="返回文档列表" aria-label="返回文档列表" @click="emit('close')">
          <el-icon><ArrowLeft /></el-icon><span>文档列表</span>
        </button>
        <div class="reader-title">
          <h2 :title="document.name">{{ document.name }}</h2>
          <span class="reader-metadata">
            <span>{{ document.knowledgeBaseName }} · {{ formattedSize(document.size) }}</span>
            <time v-if="document.updateTime" :title="formattedTime(document.updateTime)">更新于 {{ formattedTime(document.updateTime) }}</time>
            <span v-if="document.embeddingModel" class="reader-model" :title="`索引模型：${document.embeddingModel}`">{{ document.embeddingModel }}</span>
          </span>
        </div>
      </div>
      <div class="reader-actions" aria-label="文档操作">
        <el-button text title="下载" :aria-label="`下载 ${document.name}`" @click="emit('download')"><el-icon><Download /></el-icon><span class="reader-action-label">下载</span></el-button>
        <el-button text title="重新索引" :aria-label="`重新索引 ${document.name}`" :loading="reindexing" @click="emit('reindex')"><el-icon v-if="!reindexing"><RefreshRight /></el-icon><span class="reader-action-label">重新索引</span></el-button>
        <el-button text title="删除" :aria-label="`删除 ${document.name}`" class="reader-delete" @click="emit('delete')"><el-icon><Delete /></el-icon></el-button>
      </div>
      <p v-if="document.errorMessage" class="detail-error reader-index-error" role="alert">{{ document.errorMessage }}</p>
    </header>
    <nav class="reader-navigation" aria-label="文档内容">
      <div class="detail-tabs" role="tablist">
        <button id="document-preview-tab" role="tab" :aria-selected="activeTab === 'preview'" aria-controls="document-preview" @click="activeTab = 'preview'">文档预览</button>
        <button id="document-chunks-tab" role="tab" :aria-selected="activeTab === 'chunks'" aria-controls="document-chunks" @click="activeTab = 'chunks'">切片内容<span>{{ document.chunkCount || 0 }}</span></button>
      </div>
      <div class="reader-view-controls">
        <template v-if="activeTab === 'preview'">
          <span class="reader-mode">{{ showOriginal ? '原始 PDF' : '文档正文' }}</span>
          <el-button v-if="isPdf" text @click="showOriginal = !showOriginal">{{ showOriginal ? '查看正文' : '查看原始 PDF' }}</el-button>
        </template>
        <template v-else>
          <span class="chunk-page-label">{{ chunks?.total ?? document.chunkCount ?? 0 }} 个切片 · 第 {{ page }} 页</span>
          <el-button text :loading="loadingChunks" :icon="RefreshRight" @click="chunksRevision++">刷新</el-button>
        </template>
      </div>
    </nav>

    <section v-if="activeTab === 'preview'" id="document-preview" class="detail-content" role="tabpanel" aria-labelledby="document-preview-tab">
      <div v-if="showOriginal" v-loading="loadingOriginal" class="original-preview">
        <iframe v-if="originalUrl" :src="originalUrl" :title="`PDF 预览：${document.name}`" />
        <p v-else-if="originalError" class="detail-error" role="alert">{{ originalError }}</p>
      </div>
      <div v-else ref="readerScroll" v-loading="loadingPreview" class="text-preview detail-scroll" tabindex="0" aria-label="文档正文滚动区域">
        <div v-if="previewError" class="detail-error" role="alert"><p>{{ previewError }}</p><el-button text :icon="RefreshRight" @click="previewRevision++">重新加载</el-button></div>
        <div v-else-if="content && isMarkdown" class="markdown-preview markdown-body" v-html="renderedContent" />
        <pre v-else-if="content">{{ content }}</pre>
        <el-empty v-else-if="!loadingPreview" description="文档暂无可预览正文" :image-size="48"><template #image><el-icon class="detail-empty-icon"><Document /></el-icon></template></el-empty>
      </div>
    </section>

    <section v-else id="document-chunks" class="detail-content" role="tabpanel" aria-labelledby="document-chunks-tab">
      <div ref="readerScroll" v-loading="loadingChunks" class="chunk-list detail-scroll" tabindex="0" aria-label="切片内容滚动区域">
        <div v-if="chunksError" class="detail-error" role="alert"><p>{{ chunksError }}</p><el-button text :icon="RefreshRight" @click="chunksRevision++">重新加载</el-button></div>
        <div v-if="chunks?.items.length" class="chunk-rows">
          <div class="chunk-list-caption" aria-hidden="true"><span>内容摘要</span><span>点击展开全文</span></div>
          <KnowledgeChunkCard v-for="chunk in chunks.items" :key="chunk.index" :chunk="chunk"
            :matched="chunk.index === initialChunkIndex" />
        </div>
        <el-empty v-if="!loadingChunks && !chunksError && !chunks?.items.length" description="暂无切片，请先为文档建立索引" :image-size="48"><template #image><el-icon class="detail-empty-icon"><Document /></el-icon></template></el-empty>
      </div>
      <el-pagination v-if="chunks && chunks.total > pageSize" v-model:current-page="page" :page-size="pageSize" :total="chunks.total" layout="prev, pager, next" :disabled="loadingChunks" small />
    </section>
  </section>
</template>

<style scoped>
.document-reader {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  outline: none;
  background: var(--surface);
  container: reader / inline-size;
}
.reader-header { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px 16px; padding: 12px 24px; }
.reader-heading { display: flex; flex: 1; align-items: center; gap: 16px; min-width: 180px; }
.reader-back { display: inline-flex; align-items: center; justify-content: center; gap: 8px; flex-shrink: 0; min-width: 32px; min-height: 36px; padding: 4px 8px; border: 0; border-radius: 8px; color: var(--text-muted); background: transparent; cursor: pointer; }
.reader-back:hover { color: var(--text); background: var(--surface-hover); }
.reader-back > span { font-size: 12px; }
.reader-back .el-icon { font-size: 16px; }
.reader-title { min-width: 0; }
.reader-title h2 { margin: 0; overflow: hidden; color: var(--text); font-size: 18px; font-weight: 500; line-height: 1.5; text-overflow: ellipsis; white-space: nowrap; }
.reader-metadata { display: flex; flex-wrap: wrap; gap: 4px 16px; margin-top: 4px; color: var(--text-muted); font-size: 12px; line-height: 1.5; font-variant-numeric: tabular-nums; }
.reader-model { max-width: 200px; overflow: hidden; color: var(--text-muted); font-family: "SFMono-Regular", Consolas, monospace; text-overflow: ellipsis; white-space: nowrap; }
.reader-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 4px; margin-left: auto; }
.reader-actions .el-button { min-height: 32px; margin: 0; padding: 4px 8px; border-radius: 8px; color: var(--text-muted); }
.reader-actions .el-button:hover { color: var(--text); }
.reader-actions .el-button .el-icon { font-size: 16px; }
.reader-actions .reader-delete:hover { color: var(--danger-text); }
.reader-navigation { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 4px 16px; padding: 0 24px; border-bottom: 1px solid var(--line); }
.detail-tabs { display: flex; flex-shrink: 0; gap: 24px; }
.detail-tabs button { display: flex; align-items: center; gap: 8px; min-height: 44px; padding: 12px 0; border: 0; border-bottom: 2px solid transparent; color: var(--text-muted); background: transparent; font-size: 14px; cursor: pointer; }
.detail-tabs button[aria-selected='true'] { border-bottom-color: var(--primary); color: var(--text); }
.detail-tabs button > span { font-size: 12px; font-variant-numeric: tabular-nums; }
.reader-view-controls { display: flex; align-items: center; gap: 16px; color: var(--text-muted); font-size: 12px; font-variant-numeric: tabular-nums; }
.reader-view-controls .el-button { min-height: 32px; margin: 0; padding: 4px 8px; }
.detail-content { display: grid; grid-template-rows: minmax(0, 1fr) auto; min-width: 0; min-height: 0; overflow: hidden; }
.detail-scroll { min-width: 0; min-height: 0; overflow-y: auto; overscroll-behavior: contain; scrollbar-gutter: stable; }
.text-preview { padding: 24px 40px 40px; }
.text-preview pre { max-width: 880px; margin: 0 auto; color: var(--text-body); font-family: inherit; font-size: 15px; line-height: 1.9; overflow-wrap: anywhere; white-space: pre-wrap; }
.markdown-preview { max-width: 880px; margin: 0 auto; color: var(--text-body); font-size: 15px; line-height: 1.9; overflow-wrap: anywhere; }
.markdown-preview :deep(img) { display: block; max-width: 100%; height: auto; }
.original-preview { display: flex; min-width: 0; min-height: 0; padding: 16px 24px 24px; }
.original-preview iframe { display: block; width: 100%; height: 100%; min-height: 0; border: 1px solid var(--line); border-radius: 8px; }
.detail-error { color: var(--danger-text); font-size: 13px; line-height: 1.7; overflow-wrap: anywhere; }
.reader-index-error { flex-basis: 100%; margin: 0; padding: 8px 12px; border: 1px solid var(--line); border-radius: 8px; background: var(--surface-subtle); }
.chunk-list { padding: 24px; background: var(--surface-subtle); }
.chunk-rows { width: 100%; max-width: 760px; min-width: 0; margin: 0 auto; }
.chunk-list-caption { display: flex; justify-content: space-between; gap: 16px; margin-bottom: 8px; padding: 0 12px 8px 52px; color: var(--text-muted); font-size: 12px; line-height: 1.5; }
.detail-empty-icon { color: var(--text-muted); font-size: 24px; }
.el-pagination { flex-shrink: 0; justify-content: center; min-height: 44px; margin: 0; padding: 8px 24px; border-top: 1px solid var(--line); }
button:focus-visible, .detail-scroll:focus-visible { outline: 2px solid var(--primary); outline-offset: -2px; }
@container reader (max-width: 760px) {
  .reader-header { padding: 12px 16px; }
  .reader-heading { gap: 12px; }
  .reader-metadata time, .reader-model { display: none; }
  .reader-navigation { padding: 0 16px; }
  .reader-view-controls { gap: 8px; }
  .text-preview { padding: 20px 24px 32px; }
  .chunk-list { padding: 16px; }
  .original-preview { padding: 16px; }
}
@container reader (max-width: 520px) {
  .reader-header { flex-wrap: nowrap; gap: 8px; padding: 12px; }
  .reader-heading { gap: 8px; min-width: 0; }
  .reader-back { padding: 4px; }
  .reader-back > span, .reader-action-label { display: none; }
  .reader-title h2 { font-size: 16px; }
  .reader-actions { flex-wrap: nowrap; gap: 0; }
  .reader-actions .el-button { padding: 4px 6px; }
  .reader-navigation { gap: 0 8px; padding: 0 12px; }
  .detail-tabs { gap: 20px; }
  .reader-mode, .chunk-page-label { display: none; }
  .text-preview { padding: 20px 16px 24px; }
  .chunk-list { padding: 12px; }
  .chunk-list-caption { padding-left: 12px; }
  .original-preview { padding: 12px; }
}
</style>
