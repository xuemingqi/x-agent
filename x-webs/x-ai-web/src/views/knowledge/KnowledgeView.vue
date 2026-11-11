<script setup lang="ts">
import {
  Document,
  Link,
  Plus,
  Refresh,
  Search,
  UploadFilled,
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue';

import {
  deleteKnowledgeDocument,
  deletePublicKnowledgeDocument,
  downloadKnowledgeDocument,
  downloadPublicKnowledgeDocument,
  listKnowledgeDocuments,
  listPublicKnowledgeDocuments,
  reindexKnowledgeDocument,
  searchKnowledgeDocuments,
  uploadKnowledgeDocuments,
  uploadPublicKnowledgeDocuments,
} from '@/api/knowledge';
import KnowledgeDocumentReader from '@/components/knowledge/KnowledgeDocumentReader.vue';
import KnowledgeLinkPanel from '@/components/knowledge/KnowledgeLinkPanel.vue';
import { useAdministrationStore } from '@/stores/administration';
import type { KnowledgeDocument, KnowledgeIndexStatus, KnowledgeScope, KnowledgeSearchResult } from '@/types/knowledge';

const activeResource = ref<'file' | 'link'>('file');
const activeScope = ref<KnowledgeScope>('user');
const administration = useAdministrationStore();
const documents = ref<KnowledgeDocument[]>([]);
const linkCount = ref(0);
const linkPanel = ref<InstanceType<typeof KnowledgeLinkPanel>>();
const loadingDocuments = ref(false);
const uploading = ref(false);
const fileInput = ref<HTMLInputElement>();
const reindexingPaths = ref(new Set<string>());
const query = ref('');
const searching = ref(false);
const searchResults = ref<KnowledgeSearchResult[]>([]);
const searchedQuery = ref('');
const searchError = ref('');
const selectedDocument = ref<KnowledgeDocument | null>(null);
const detailTab = ref<'preview' | 'chunks'>('preview');
const selectedChunkIndex = ref<number>();
const readerRevision = ref(0);
const catalogElement = ref<HTMLElement>();
const readyCount = computed(() => documents.value.filter((document) => document.status === 'READY').length);
let listRevision = 0;
let searchRevision = 0;
let disposed = false;

async function loadDocuments(): Promise<void> {
  const revision = ++listRevision;
  const scope = activeScope.value;
  loadingDocuments.value = true;
  try {
    const result = await (scope === 'public'
      ? listPublicKnowledgeDocuments()
      : listKnowledgeDocuments());
    if (!disposed && revision === listRevision) {
      documents.value = result;
      if (selectedDocument.value) {
        selectedDocument.value = result.find((document) => document.path === selectedDocument.value?.path) || null;
      }
    }
  } catch (error) {
    if (!disposed && revision === listRevision) {
      ElMessage.error(error instanceof Error ? error.message : '文件知识库加载失败');
    }
  } finally {
    if (!disposed && revision === listRevision) loadingDocuments.value = false;
  }
}

function chooseFiles(): void {
  fileInput.value?.click();
}

async function uploadFiles(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files || []);
  input.value = '';
  if (!files.length || uploading.value) return;
  const supported = /\.(txt|md|csv|json|html?|pdf|docx)$/i;
  if (files.some((file) => !supported.test(file.name))) {
    ElMessage.warning('支持上传 TXT、Markdown、CSV、JSON、HTML、PDF 和 DOCX 文档');
    return;
  }
  const scope = activeScope.value;
  uploading.value = true;
  try {
    const uploaded = await (scope === 'public'
      ? uploadPublicKnowledgeDocuments(files)
      : uploadKnowledgeDocuments(files));
    const failed = uploaded.filter((document) => document.status === 'FAILED').length;
    if (failed) ElMessage.warning(`已保存 ${uploaded.length} 个文档，${failed} 个索引失败，可重新索引`);
    else ElMessage.success(`已上传并索引 ${uploaded.length} 个文档`);
    if (!disposed && activeScope.value === scope) {
      clearSearch();
      await loadDocuments();
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '上传失败');
  } finally {
    uploading.value = false;
  }
}

async function downloadDocument(document: KnowledgeDocument): Promise<void> {
  try {
    const blob = await (activeScope.value === 'public'
      ? downloadPublicKnowledgeDocument(document.path)
      : downloadKnowledgeDocument(document.path));
    const link = window.document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = document.name;
    link.click();
    URL.revokeObjectURL(link.href);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '下载失败');
  }
}

async function removeDocument(document: KnowledgeDocument): Promise<void> {
  const scope = activeScope.value;
  try {
    await ElMessageBox.confirm(`确定删除“${document.name}”吗？`, '删除文档', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    });
    await (scope === 'public'
      ? deletePublicKnowledgeDocument(document.path)
      : deleteKnowledgeDocument(document.path));
    ElMessage.success('文档已删除');
    if (!disposed && activeScope.value === scope) {
      clearSearch();
      await loadDocuments();
    }
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '删除失败');
    }
  }
}

function openDocument(document: KnowledgeDocument, tab: 'preview' | 'chunks' = 'preview'): void {
  readerRevision.value++;
  selectedDocument.value = document;
  detailTab.value = tab;
  selectedChunkIndex.value = undefined;
}

async function closeDocument(): Promise<void> {
  const path = selectedDocument.value?.path;
  selectedDocument.value = null;
  await nextTick();
  Array.from(catalogElement.value?.querySelectorAll<HTMLButtonElement>('.document-name') || [])
    .find((button) => button.dataset.path === path)?.focus();
}

function openSearchResult(result: KnowledgeSearchResult): void {
  const document = documents.value.find((item) => item.path === result.path);
  if (document) {
    openDocument(document, 'chunks');
    selectedChunkIndex.value = result.chunkIndex;
  }
}

async function reindexDocument(document: KnowledgeDocument): Promise<void> {
  if (reindexingPaths.value.has(document.path)) return;
  const scope = activeScope.value;
  const pendingPaths = reindexingPaths.value;
  pendingPaths.add(document.path);
  try {
    const result = await reindexKnowledgeDocument(document.path, scope);
    if (result.status === 'FAILED') ElMessage.error(result.errorMessage || '索引失败，请检查向量化服务后重试');
    else ElMessage.success('文档索引已更新');
    if (!disposed && activeScope.value === scope) {
      clearSearch();
      await loadDocuments();
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '重新索引失败');
  } finally {
    pendingPaths.delete(document.path);
  }
}

function clearSearch(): void {
  searchRevision++;
  searching.value = false;
  searchedQuery.value = '';
  searchError.value = '';
  searchResults.value = [];
}

async function searchDocuments(): Promise<void> {
  const text = query.value.trim();
  if (!text) { clearSearch(); return; }
  if (searching.value) return;
  const revision = ++searchRevision;
  const scope = activeScope.value;
  searching.value = true;
  searchError.value = '';
  searchedQuery.value = text;
  searchResults.value = [];
  try {
    const result = await searchKnowledgeDocuments(text, scope);
    if (!disposed && revision === searchRevision) searchResults.value = result;
  } catch (error) {
    if (!disposed && revision === searchRevision) {
      searchError.value = error instanceof Error ? error.message : '检索失败，请稍后重试';
    }
  } finally {
    if (!disposed && revision === searchRevision) searching.value = false;
  }
}

function statusName(status: KnowledgeIndexStatus): string {
  return ({ READY: '已索引', INDEXING: '索引中', FAILED: '索引失败', UNINDEXED: '未索引' })[status] || '未索引';
}

function similarity(score: number): string {
  return Number.isFinite(score) ? `${(score * 100).toFixed(1)}%` : '—';
}

function formatSize(size: number): string {
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

function formatTime(value: string): string {
  return value ? new Date(value).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' }) : '--';
}

async function changeScope(scope: KnowledgeScope): Promise<void> {
  if (scope === activeScope.value) return;
  activeScope.value = scope;
  documents.value = [];
  linkCount.value = 0;
  selectedDocument.value = null;
  reindexingPaths.value = new Set();
  query.value = '';
  clearSearch();
  if (!disposed) await loadDocuments();
}

onMounted(async () => {
  try {
    await administration.load();
  } catch {
    // 管理员状态不可用时保持普通用户界面，后端仍会拒绝公共写操作。
  }
  if (!disposed) await loadDocuments();
});

onUnmounted(() => { disposed = true; });
</script>

<template>
  <section class="workspace-page knowledge-page">
    <header class="page-header knowledge-header">
      <div>
        <h1>知识库</h1>
        <span>整理知识资料，在文档与切片之间核对检索依据</span>
      </div>
      <el-button
        v-if="activeResource === 'file'" class="page-primary-action" type="primary"
        :loading="uploading" @click="chooseFiles"
      >
        <el-icon><UploadFilled /></el-icon>
        {{ uploading ? '解析并索引中…' : '上传文档' }}
      </el-button>
      <el-button v-else class="page-primary-action" type="primary" @click="linkPanel?.openCreateLink()">
        <el-icon><Plus /></el-icon>添加在线文档
      </el-button>
      <input
        ref="fileInput" class="hidden-file-input" type="file" multiple
        accept=".txt,.md,.csv,.json,.html,.htm,.pdf,.docx" @change="uploadFiles"
      />
    </header>

    <div class="knowledge-controls">
      <nav class="knowledge-tabs" aria-label="知识类型">
        <button :class="{ active: activeResource === 'file' }" :aria-current="activeResource === 'file' ? 'page' : undefined" @click="activeResource = 'file'">
          <el-icon><Document /></el-icon>文档知识<span>{{ documents.length }}</span>
        </button>
        <button :class="{ active: activeResource === 'link' }" :aria-current="activeResource === 'link' ? 'page' : undefined" @click="activeResource = 'link'">
          <el-icon><Link /></el-icon>在线文档<span>{{ linkCount }}</span>
        </button>
      </nav>
      <div v-if="administration.isAdministrator" class="scope-switch" aria-label="知识范围">
        <button :class="{ active: activeScope === 'user' }" :aria-pressed="activeScope === 'user'" @click="changeScope('user')">个人知识库</button>
        <button :class="{ active: activeScope === 'public' }" :aria-pressed="activeScope === 'public'" @click="changeScope('public')">公共知识库</button>
      </div>
    </div>

    <div v-if="activeResource === 'file'" class="knowledge-workspace" :class="{ 'has-document': selectedDocument }">
      <aside ref="catalogElement" class="knowledge-catalog" aria-label="文档目录与检索">
        <div class="catalog-header">
          <div><strong>{{ activeScope === 'public' ? '公共文档' : '我的文档' }}</strong><span>{{ documents.length }} 份 · {{ readyCount }} 份已索引</span></div>
          <el-button text :loading="loadingDocuments" title="刷新文档列表" aria-label="刷新文档列表" @click="loadDocuments">
            <el-icon><Refresh /></el-icon>
          </el-button>
        </div>
        <form class="search-form" role="search" @submit.prevent="searchDocuments">
          <el-input
            v-model="query" :prefix-icon="Search" clearable maxlength="2000"
            placeholder="输入问题，检索文档内容" aria-label="知识库检索问题" @clear="clearSearch"
          />
          <el-button native-type="submit" :loading="searching" :disabled="!query.trim()">检索</el-button>
        </form>
        <div class="catalog-body">
          <section v-if="searchedQuery" v-loading="searching" class="search-results" aria-label="检索结果" aria-live="polite">
            <header>
              <div><strong>检索结果</strong><span>{{ searchResults.length }} 个相关切片</span></div>
              <button type="button" class="catalog-text-action" @click="clearSearch">全部文档</button>
            </header>
            <p class="searched-query">“{{ searchedQuery }}”</p>
            <p v-if="searchError" class="index-error" role="alert">{{ searchError }}</p>
            <button
              v-for="(result, index) in searchResults" :key="`${result.scope}-${result.path}-${result.chunkIndex}-${index}`"
              type="button" class="search-result"
              :class="{ selected: selectedDocument?.path === result.path && selectedChunkIndex === result.chunkIndex }"
              @click="openSearchResult(result)"
            >
              <strong>{{ result.name }}</strong>
              <span class="result-heading"><span>切片 {{ result.chunkIndex }}</span><span>相似度 {{ similarity(result.score) }}</span></span>
              <span class="result-content">{{ result.content }}</span>
            </button>
            <p v-if="!searching && !searchError && !searchResults.length" class="search-empty">没有找到相关切片，试着换一种描述，或确认文档已完成索引</p>
          </section>
          <div v-else v-loading="loadingDocuments" class="document-list" aria-label="知识库文档">
            <article
              v-for="document in documents" :key="document.path" class="document-row"
              :class="{ selected: selectedDocument?.path === document.path }"
            >
              <button
                class="document-name" type="button" :data-path="document.path"
                :aria-pressed="selectedDocument?.path === document.path" :title="document.path"
                @click="openDocument(document)"
              >
                <el-icon><Document /></el-icon>
                <span class="document-summary">
                  <strong>{{ document.name }}</strong>
                  <span class="document-facts"><span class="index-status" :class="document.status?.toLowerCase()">{{ statusName(document.status) }}</span><span>{{ formatSize(document.size) }}</span><time :title="document.updateTime">{{ formatTime(document.updateTime) }}</time></span>
                  <span v-if="document.errorMessage" class="index-error" :title="document.errorMessage">{{ document.errorMessage }}</span>
                </span>
              </button>
              <button
                v-if="document.status === 'READY'" type="button" class="chunk-count"
                :aria-label="`查看 ${document.name} 的 ${document.chunkCount} 个切片`"
                @click="openDocument(document, 'chunks')"
              >{{ document.chunkCount }} 切片</button>
            </article>
            <div v-if="!loadingDocuments && !documents.length" class="catalog-empty">
              <el-icon><Document /></el-icon>
              <strong>还没有文档</strong>
              <p>上传一份资料，开始阅读与检索</p>
              <el-button @click="chooseFiles">上传文档</el-button>
            </div>
          </div>
        </div>
        <p class="upload-hint">TXT · Markdown · CSV · JSON · HTML · PDF · DOCX</p>
      </aside>

      <KnowledgeDocumentReader
        v-if="selectedDocument" :key="`${activeScope}-${selectedDocument.path}-${readerRevision}`"
        :document="selectedDocument" :scope="activeScope" :initial-tab="detailTab" :initial-chunk-index="selectedChunkIndex"
        :reindexing="reindexingPaths.has(selectedDocument.path)"
        @close="closeDocument" @download="downloadDocument(selectedDocument)"
        @reindex="reindexDocument(selectedDocument)" @delete="removeDocument(selectedDocument)"
      />
      <section v-else class="knowledge-empty-reader" aria-label="文档阅读区">
        <el-icon><Document /></el-icon>
        <h2>从一份文档开始</h2>
        <p>在左侧选择文档，阅读正文、查看切片<br />也可以输入问题，定位相关知识的来源</p>
        <span>{{ activeScope === 'public' ? '公共知识可用于所有用户的对话' : '个人文档仅当前用户可见' }}</span>
      </section>
    </div>

    <KnowledgeLinkPanel
      v-show="activeResource === 'link'" ref="linkPanel" :public-mode="activeScope === 'public'"
      @count-change="linkCount = $event"
    />
  </section>
</template>

<style scoped>
.knowledge-page {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}
.knowledge-header { flex-shrink: 0; margin-bottom: 24px; }
.knowledge-header > div { min-width: 0; }
.knowledge-header .el-button { flex-shrink: 0; }
.knowledge-controls {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px 24px;
  width: 100%;
  max-width: var(--workspace-max-width, 1440px);
  margin: 0 auto 20px;
  border-bottom: 1px solid var(--line);
}
.knowledge-controls .knowledge-tabs {
  width: auto;
  margin: 0;
  padding: 0;
  gap: 24px;
  border: 0;
  border-radius: 0;
  background: transparent;
}
.knowledge-tabs button {
  min-height: 44px;
  padding: 0 0 12px;
  border-bottom: 2px solid transparent;
  border-radius: 0;
  font-size: 14px;
}
.knowledge-tabs button.active { border-bottom-color: var(--primary); background: transparent; }
.knowledge-tabs button > span { margin: 0; padding: 0; background: transparent; font-size: 12px; font-variant-numeric: tabular-nums; }
.knowledge-controls .scope-switch { width: auto; margin: 0 0 12px; padding: 0; gap: 4px; border: 0; background: transparent; }
.scope-switch button { min-height: 32px; padding: 4px 12px; border-radius: 8px; font-size: 12px; }
.scope-switch button.active::after { display: none; }
.knowledge-workspace {
  display: grid;
  flex: 1;
  grid-template-columns: minmax(280px, 336px) minmax(0, 1fr);
  width: 100%;
  max-width: var(--workspace-max-width, 1440px);
  min-height: 0;
  margin: 0 auto;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface);
}
.knowledge-catalog { display: flex; flex-direction: column; min-width: 0; min-height: 0; border-right: 1px solid var(--line); background: var(--surface-subtle); }
.catalog-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 16px 16px 12px; }
.catalog-header > div, .search-results > header > div { display: flex; align-items: baseline; flex-wrap: wrap; gap: 4px 12px; }
.catalog-header strong, .search-results > header strong { color: var(--text); font-size: 13px; font-weight: 500; }
.catalog-header span, .search-results > header span { color: var(--text-muted); font-size: 12px; font-variant-numeric: tabular-nums; }
.search-form { display: flex; gap: 8px; padding: 0 16px 16px; }
.search-form .el-input { flex: 1; min-width: 0; }
.search-form .el-button { margin: 0; padding: 0 12px; }
.catalog-body { flex: 1; min-height: 0; overflow-y: auto; scrollbar-gutter: stable; }
.document-list { min-height: 160px; padding: 0 8px 8px; }
.document-row { display: flex; align-items: center; gap: 8px; padding: 4px 12px 4px 8px; border: 1px solid transparent; border-radius: 8px; }
.document-row + .document-row { margin-top: 4px; }
.document-row:hover { background: var(--surface-hover); }
.document-row.selected { border-color: var(--line-strong); background: var(--surface); }
.document-name { display: flex; flex: 1; align-items: flex-start; gap: 12px; min-width: 0; min-height: 64px; padding: 12px 0; border: 0; color: var(--text); background: transparent; text-align: left; cursor: pointer; }
.document-name > .el-icon { flex-shrink: 0; margin-top: 4px; color: var(--text-muted); font-size: 16px; }
.document-summary { display: flex; flex: 1; flex-direction: column; gap: 8px; min-width: 0; }
.document-summary > strong { overflow: hidden; font-size: 13px; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.document-facts { display: flex; flex-wrap: wrap; gap: 4px 8px; color: var(--text-muted); font-size: 12px; font-variant-numeric: tabular-nums; }
.document-facts time { margin-left: auto; }
.index-status.ready { color: var(--success-text); }
.index-status.failed, .index-error { color: var(--danger-text); }
.index-status.indexing { color: var(--warning-text); }
.document-summary > .index-error { overflow: hidden; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.chunk-count, .catalog-text-action { padding: 4px 0; border: 0; color: var(--text-muted); background: transparent; font-size: 12px; font-variant-numeric: tabular-nums; white-space: nowrap; cursor: pointer; }
.chunk-count:hover, .catalog-text-action:hover { color: var(--primary); }
.catalog-empty { display: flex; min-height: 280px; flex-direction: column; align-items: center; justify-content: center; gap: 12px; padding: 24px; text-align: center; }
.catalog-empty > .el-icon { color: var(--text-muted); font-size: 24px; }
.catalog-empty strong { font-size: 14px; font-weight: 500; }
.catalog-empty p { margin: 0 0 4px; color: var(--text-muted); font-size: 12px; line-height: 1.7; }
.upload-hint { flex-shrink: 0; margin: 0; padding: 12px 16px; border-top: 1px solid var(--line); color: var(--text-muted); font-size: 12px; line-height: 1.7; }
.search-results { padding: 0 16px 16px; }
.search-results > header { display: flex; justify-content: space-between; gap: 12px; align-items: flex-start; padding: 4px 0 8px; }
.searched-query { margin: 0 0 12px; color: var(--text-muted); font-size: 12px; overflow-wrap: anywhere; line-height: 1.7; }
.search-result { display: block; width: 100%; margin-top: 8px; padding: 12px; border: 1px solid var(--line); border-radius: 8px; color: var(--text); background: var(--surface); text-align: left; cursor: pointer; }
.search-result:hover, .search-result.selected { border-color: var(--primary-border-strong); }
.search-result > strong { display: block; font-size: 13px; font-weight: 500; overflow-wrap: anywhere; }
.result-heading { display: flex; justify-content: space-between; gap: 8px; margin-top: 8px; color: var(--text-muted); font-size: 12px; font-variant-numeric: tabular-nums; }
.result-content { display: -webkit-box; margin-top: 8px; overflow: hidden; color: var(--text-soft); font-size: 13px; line-height: 1.7; white-space: pre-wrap; overflow-wrap: anywhere; -webkit-line-clamp: 4; -webkit-box-orient: vertical; }
.search-empty, .search-results > .index-error { margin: 0; font-size: 13px; line-height: 1.7; }
.search-empty { color: var(--text-muted); }
.knowledge-empty-reader { display: flex; min-width: 0; flex-direction: column; align-items: center; justify-content: center; gap: 16px; padding: 32px; text-align: center; }
.knowledge-empty-reader > .el-icon { color: var(--text-muted); font-size: 32px; }
.knowledge-empty-reader h2 { margin: 0; font-size: 20px; font-weight: 500; }
.knowledge-empty-reader p { margin: 0; color: var(--text-soft); font-size: 14px; line-height: 1.8; }
.knowledge-empty-reader > span { margin-top: 8px; color: var(--text-muted); font-size: 12px; }
button:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
.knowledge-page > .knowledge-link-panel { flex: 1; min-height: 0; overflow-y: auto; }
@container workspace (max-width: 900px) {
  .knowledge-workspace { grid-template-columns: minmax(0, 1fr); }
  .knowledge-catalog { border-right: 0; }
  .knowledge-empty-reader, .has-document .knowledge-catalog { display: none; }
  .knowledge-controls { gap: 4px 16px; }
}
@container workspace (max-width: 520px) {
  .knowledge-header { flex-direction: column; align-items: flex-start; gap: 16px; margin-bottom: 16px; }
  .knowledge-header > .el-button { width: 100%; }
  .knowledge-controls .knowledge-tabs { gap: 20px; }
  .knowledge-controls .scope-switch { margin-bottom: 8px; }
  .knowledge-empty-reader { padding: 24px; }
}
</style>
