<script setup lang="ts">
import {
  Document,
  FolderOpened,
  Link,
  Plus,
  Refresh,
  Search,
  UploadFilled,
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue';

import {
  createKnowledgeBase,
  deleteKnowledgeBase,
  deleteKnowledgeDocument,
  downloadKnowledgeDocument,
  listKnowledgeBases,
  listKnowledgeDocuments,
  reindexKnowledgeDocument,
  searchKnowledgeDocuments,
  uploadKnowledgeDocuments,
  updateKnowledgeBase,
} from '@/api/knowledge';
import KnowledgeDocumentReader from '@/components/knowledge/KnowledgeDocumentReader.vue';
import KnowledgeLinkPanel from '@/components/knowledge/KnowledgeLinkPanel.vue';
import type { KnowledgeBase, KnowledgeDocument, KnowledgeIndexStatus, KnowledgeSearchResult } from '@/types/knowledge';

const activeResource = ref<'file' | 'link'>('file');
const knowledgeBases = ref<KnowledgeBase[]>([]);
const orderedBases = computed(() => [...knowledgeBases.value].sort((left, right) => Number(right.isDefault) - Number(left.isDefault)));
const selectedBaseId = ref('');
const selectedBase = computed(() => knowledgeBases.value.find((base) => base.id === selectedBaseId.value));
const loadingBases = ref(false);
const savingBase = ref(false);
const basesError = ref('');
const documents = ref<KnowledgeDocument[]>([]);
const linkCount = ref(0);
const linkPanel = ref<InstanceType<typeof KnowledgeLinkPanel>>();
const loadingDocuments = ref(false);
const documentsError = ref('');
const uploading = ref(false);
const fileInput = ref<HTMLInputElement>();
const reindexingPaths = ref(new Set<string>());
const query = ref('');
const searching = ref(false);
const searchResults = ref<KnowledgeSearchResult[]>([]);
const searchedQuery = ref('');
const searchError = ref('');
const selectedDocument = ref<KnowledgeDocument | null>(null);
const isReading = computed(() => activeResource.value === 'file' && selectedDocument.value !== null);
const detailTab = ref<'preview' | 'chunks'>('preview');
const selectedChunkIndex = ref<number>();
const readerRevision = ref(0);
const catalogElement = ref<HTMLElement>();
const readyCount = computed(() => documents.value.filter((document) => document.status === 'READY').length);
let listRevision = 0;
let searchRevision = 0;
let disposed = false;

async function loadBases(preferredId = selectedBaseId.value): Promise<void> {
  loadingBases.value = true;
  basesError.value = '';
  try {
    const bases = await listKnowledgeBases();
    if (disposed) return;
    knowledgeBases.value = bases;
    const baseId = bases.find((base) => base.id === preferredId)?.id || orderedBases.value[0]?.id || '';
    if (baseId !== selectedBaseId.value) selectBase(baseId);
  } catch (error) {
    if (!disposed) basesError.value = error instanceof Error ? error.message : '知识库加载失败';
  } finally {
    if (!disposed) loadingBases.value = false;
  }
}

function selectBase(id: string): void {
  activeResource.value = 'file';
  if (id === selectedBaseId.value) return;
  selectedBaseId.value = id;
  documents.value = [];
  selectedDocument.value = null;
  query.value = '';
  clearSearch();
  void loadDocuments();
}

async function saveBase(create: boolean): Promise<void> {
  const base = selectedBase.value;
  if ((!create && !base) || savingBase.value) return;
  try {
    const { value } = await ElMessageBox.prompt('知识库名称', create ? '创建知识库' : '重命名知识库', {
      inputValue: create ? '' : base?.name,
      inputValidator: (value: string) => Boolean(value?.trim()) && value.trim().length <= 100 || '请输入 1–100 个字符',
      confirmButtonText: create ? '创建' : '保存',
      cancelButtonText: '取消',
    });
    savingBase.value = true;
    const payload = { name: value.trim(), description: create ? '' : base!.description };
    const saved = create ? await createKnowledgeBase(payload) : await updateKnowledgeBase(base!.id, payload);
    await loadBases(saved.id);
    ElMessage.success(create ? '知识库已创建' : '知识库已更新');
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '保存知识库失败');
  } finally {
    savingBase.value = false;
  }
}

async function removeBase(): Promise<void> {
  const base = selectedBase.value;
  if (!base || base.isDefault || savingBase.value) return;
  try {
    await ElMessageBox.confirm(`删除“${base.name}”及其中全部文档和切片？关联该知识库的对话将无法再检索这些资料。`, '删除知识库', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning',
    });
    savingBase.value = true;
    await deleteKnowledgeBase(base.id);
    await loadBases();
    ElMessage.success('知识库已删除');
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '删除知识库失败');
  } finally {
    savingBase.value = false;
  }
}

async function loadDocuments(): Promise<void> {
  const revision = ++listRevision;
  const baseId = selectedBaseId.value;
  if (!baseId) { loadingDocuments.value = false; return; }
  loadingDocuments.value = true;
  documentsError.value = '';
  try {
    const result = await listKnowledgeDocuments(baseId);
    if (!disposed && revision === listRevision) {
      documents.value = result;
      if (selectedDocument.value) {
        selectedDocument.value = result.find((document) => document.path === selectedDocument.value?.path) || null;
      }
    }
  } catch (error) {
    if (!disposed && revision === listRevision) {
      documentsError.value = error instanceof Error ? error.message : '文档加载失败';
    }
  } finally {
    if (!disposed && revision === listRevision) loadingDocuments.value = false;
  }
}

function chooseFiles(): void {
  if (selectedBaseId.value) fileInput.value?.click();
}

async function uploadFiles(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files || []);
  input.value = '';
  if (!files.length || uploading.value || !selectedBaseId.value) return;
  const baseId = selectedBaseId.value;
  const supported = /\.(txt|md|csv|json|html?|pdf|docx)$/i;
  if (files.some((file) => !supported.test(file.name))) {
    ElMessage.warning('支持上传 TXT、Markdown、CSV、JSON、HTML、PDF 和 DOCX 文档');
    return;
  }
  uploading.value = true;
  try {
    const uploaded = await uploadKnowledgeDocuments(baseId, files);
    const failed = uploaded.filter((document) => document.status === 'FAILED').length;
    if (failed) ElMessage.warning(`已保存 ${uploaded.length} 个文档，${failed} 个索引失败，可重新索引`);
    else ElMessage.success(`已上传并索引 ${uploaded.length} 个文档`);
    if (!disposed && baseId === selectedBaseId.value) {
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
    const blob = await downloadKnowledgeDocument(document.knowledgeBaseId, document.path);
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
  try {
    await ElMessageBox.confirm(`确定删除“${document.name}”吗？`, '删除文档', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    });
    await deleteKnowledgeDocument(document.knowledgeBaseId, document.path);
    ElMessage.success('文档已删除');
    if (!disposed && document.knowledgeBaseId === selectedBaseId.value) {
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
  const pendingKey = `${document.knowledgeBaseId}/${document.path}`;
  if (reindexingPaths.value.has(pendingKey)) return;
  const pendingPaths = reindexingPaths.value;
  pendingPaths.add(pendingKey);
  try {
    const result = await reindexKnowledgeDocument(document.knowledgeBaseId, document.path);
    if (result.status === 'FAILED') ElMessage.error(result.errorMessage || '索引失败，请检查向量化服务后重试');
    else ElMessage.success('文档索引已更新');
    if (!disposed && document.knowledgeBaseId === selectedBaseId.value) {
      clearSearch();
      await loadDocuments();
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '重新索引失败');
  } finally {
    pendingPaths.delete(pendingKey);
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
  if (searching.value || !selectedBaseId.value) return;
  const revision = ++searchRevision;
  searching.value = true;
  searchError.value = '';
  searchedQuery.value = text;
  searchResults.value = [];
  try {
    const result = await searchKnowledgeDocuments(selectedBaseId.value, text);
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

onMounted(() => loadBases());

onUnmounted(() => { disposed = true; });
</script>

<template>
  <section class="workspace-page knowledge-page" :class="{ 'is-reading': isReading }">
    <header class="page-header knowledge-header">
      <div>
        <h1>知识库</h1>
        <span>管理用于对话检索的个人资料</span>
      </div>
    </header>
    <input
      ref="fileInput" class="hidden-file-input" type="file" multiple
      accept=".txt,.md,.csv,.json,.html,.htm,.pdf,.docx" @change="uploadFiles"
    />

    <div class="knowledge-library-layout">
      <aside class="knowledge-library-sidebar" aria-label="知识库目录">
        <div class="library-sidebar-heading"><h2>我的知识库</h2><span>{{ knowledgeBases.length }}</span></div>
        <nav class="knowledge-base-list" aria-label="知识库列表" :aria-busy="loadingBases">
          <p v-if="loadingBases && !knowledgeBases.length" class="library-placeholder" role="status">正在加载知识库…</p>
          <button
            v-for="base in orderedBases" :key="base.id" type="button" class="knowledge-base-item"
            :class="{ 'common-base': base.isDefault }" :data-knowledge-base-id="base.id"
            :aria-label="base.name" :aria-pressed="activeResource === 'file' && selectedBaseId === base.id"
            aria-controls="knowledge-base-content" :disabled="loadingBases || savingBase"
            @click="selectBase(base.id)"
          >
            <el-icon><FolderOpened /></el-icon>
            <span class="knowledge-base-summary">
              <span class="knowledge-base-name">{{ base.name }}<span v-if="base.isDefault" class="common-base-label">公共</span></span>
              <span class="knowledge-base-description">{{ base.isDefault ? '所有会话自动检索' : base.description || '在对话中按需关联' }}</span>
            </span>
          </button>
          <p v-if="!loadingBases && !basesError && !knowledgeBases.length" class="library-placeholder">创建一个知识库，开始整理资料</p>
          <p v-if="basesError" class="base-error" role="alert">{{ basesError }} <button type="button" @click="loadBases()">重试</button></p>
        </nav>
        <el-button class="create-base-action" :disabled="savingBase || loadingBases" @click="saveBase(true)">
          <el-icon><Plus /></el-icon>创建知识库
        </el-button>
        <div class="library-sidebar-footer">
          <button type="button" class="online-links-entry" :aria-pressed="activeResource === 'link'" @click="activeResource = 'link'">
            <el-icon><Link /></el-icon><span>在线链接</span><span class="online-link-count">{{ linkCount }}</span>
          </button>
          <p>个人收藏的在线文档链接</p>
        </div>
      </aside>

      <div class="knowledge-library-content">
        <section v-if="activeResource === 'file'" id="knowledge-base-content" class="knowledge-documents" aria-label="当前知识库">
          <header class="knowledge-base-header">
            <div class="knowledge-base-heading">
              <h2>{{ selectedBase?.name || (loadingBases ? '加载知识库…' : '选择一个知识库') }}</h2>
              <span v-if="selectedBase?.isDefault" class="base-scope"><span class="scope-indicator" />所有会话自动检索 · 仅自己可见</span>
              <span v-else-if="selectedBase" class="base-scope">在对话中关联后检索 · 仅自己可见</span>
              <p v-if="selectedBase?.description" class="base-description">{{ selectedBase.description }}</p>
            </div>
            <div class="base-actions">
              <el-button :disabled="!selectedBase || savingBase || loadingBases" @click="saveBase(false)">重命名</el-button>
              <el-button v-if="selectedBase && !selectedBase.isDefault" class="delete-base-action" :disabled="savingBase || loadingBases" @click="removeBase">删除知识库</el-button>
              <el-button class="page-primary-action" type="primary" :loading="uploading"
                :disabled="!selectedBaseId || loadingBases || savingBase" @click="chooseFiles">
                <el-icon><UploadFilled /></el-icon>{{ uploading ? '解析并索引中…' : '上传文档' }}
              </el-button>
            </div>
          </header>

          <div class="knowledge-workspace" :class="{ 'has-document': selectedDocument, 'is-empty': !loadingDocuments && !documents.length && !searchedQuery && !documentsError }">
            <aside ref="catalogElement" class="knowledge-catalog" aria-label="文档目录与检索">
              <div class="catalog-header">
                <div><strong>文档</strong><span>{{ documents.length }} 份 · {{ readyCount }} 份已索引</span></div>
                <el-button text :loading="loadingDocuments" title="刷新文档列表" aria-label="刷新文档列表" @click="loadDocuments">
                  <el-icon><Refresh /></el-icon>
                </el-button>
              </div>
              <form class="search-form" role="search" @submit.prevent="searchDocuments">
                <el-input
                  v-model="query" :prefix-icon="Search" clearable maxlength="2000"
                  placeholder="输入问题，检索文档内容" aria-label="知识库检索问题" @clear="clearSearch"
                />
                <el-button native-type="submit" :loading="searching" :disabled="!query.trim() || !selectedBaseId">检索</el-button>
              </form>
              <div class="catalog-body">
                <div v-if="documentsError" class="catalog-load-error" role="alert">
                  <p>{{ documentsError }}</p><el-button @click="loadDocuments">重新加载</el-button>
                </div>
                <section v-if="searchedQuery" v-loading="searching" class="search-results" aria-label="检索结果" aria-live="polite">
                  <header>
                    <div><strong>检索结果</strong><span>{{ searchResults.length }} 个相关切片</span></div>
                    <button type="button" class="catalog-text-action" @click="clearSearch">全部文档</button>
                  </header>
                  <p class="searched-query">“{{ searchedQuery }}”</p>
                  <p v-if="searchError" class="index-error" role="alert">{{ searchError }}</p>
                  <button
                    v-for="(result, index) in searchResults" :key="`${result.path}-${result.chunkIndex}-${index}`"
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
                    <el-button :disabled="!selectedBaseId" @click="chooseFiles">上传文档</el-button>
                  </div>
                </div>
              </div>
              <p class="upload-hint">TXT · Markdown · CSV · JSON · HTML · PDF · DOCX</p>
            </aside>

            <KnowledgeDocumentReader
              v-if="selectedDocument" :key="`${selectedDocument.knowledgeBaseId}-${selectedDocument.path}-${readerRevision}`"
              :document="selectedDocument" :initial-tab="detailTab" :initial-chunk-index="selectedChunkIndex"
              :reindexing="reindexingPaths.has(`${selectedDocument.knowledgeBaseId}/${selectedDocument.path}`)"
              @close="closeDocument" @download="downloadDocument(selectedDocument)"
              @reindex="reindexDocument(selectedDocument)" @delete="removeDocument(selectedDocument)"
            />
            <section v-else class="knowledge-empty-reader" aria-label="文档阅读区">
              <el-icon><Document /></el-icon>
              <template v-if="!loadingDocuments && !documents.length && !documentsError">
                <h3>{{ selectedBase ? '把资料放进这个知识库' : '从一个知识库开始' }}</h3>
                <p>{{ selectedBase?.isDefault ? '上传常用资料，让每次对话都有据可查' : '按主题整理文档，需要时在对话中关联使用' }}</p>
                <el-button :disabled="!selectedBaseId || loadingBases || savingBase" @click="chooseFiles"><el-icon><Plus /></el-icon>上传第一份文档</el-button>
                <span>支持 TXT、Markdown、CSV、JSON、HTML、PDF、DOCX</span>
              </template>
              <template v-else>
                <h3>选择文档，开始阅读</h3>
                <p>在左侧浏览文档，或输入问题检索相关内容<br />正文、切片和来源都会展示在这里</p>
                <span>当前知识库的资料仅自己可见</span>
              </template>
            </section>
          </div>
        </section>
        <section v-show="activeResource === 'link'" class="knowledge-links" aria-label="个人在线链接">
          <header class="knowledge-base-header">
            <div class="knowledge-base-heading"><h2>在线链接</h2><span class="base-scope">收藏在线文档，按文件夹整理</span></div>
            <el-button class="page-primary-action" type="primary" @click="linkPanel?.openCreateLink()"><el-icon><Plus /></el-icon>添加在线文档</el-button>
          </header>
          <KnowledgeLinkPanel ref="linkPanel" @count-change="linkCount = $event" />
        </section>
      </div>
    </div>
  </section>
</template>

<style scoped>
.knowledge-page {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}
.knowledge-header { flex-shrink: 0; margin-bottom: 20px; }
.knowledge-header > div { min-width: 0; }
.knowledge-library-layout {
  display: grid;
  flex: 1;
  grid-template-columns: 224px minmax(0, 1fr);
  width: 100%;
  max-width: var(--workspace-max-width, 1440px);
  min-height: 0;
  margin: 0 auto;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface);
}
.knowledge-library-sidebar { display: flex; flex-direction: column; gap: 16px; min-height: 0; padding: 20px 12px 12px; border-right: 1px solid var(--line); background: var(--surface-subtle); }
.library-sidebar-heading { display: flex; align-items: center; justify-content: space-between; padding: 0 8px; }
.library-sidebar-heading h2 { margin: 0; font-size: 13px; font-weight: 500; }
.library-sidebar-heading > span { color: var(--text-muted); font-size: 12px; font-variant-numeric: tabular-nums; }
.knowledge-base-list { flex: 1; min-height: 0; overflow-y: auto; overscroll-behavior: contain; }
.knowledge-base-item { display: flex; align-items: flex-start; gap: 12px; width: 100%; min-height: 68px; padding: 12px; border: 1px solid transparent; border-radius: 8px; color: var(--text-soft); background: transparent; text-align: left; cursor: pointer; }
.knowledge-base-item + .knowledge-base-item { margin-top: 4px; }
.knowledge-base-item > .el-icon { flex: 0 0 auto; margin-top: 2px; font-size: 18px; }
.knowledge-base-item:hover, .online-links-entry:hover { background: var(--surface-hover); }
.knowledge-base-item[aria-pressed='true'] { border-color: var(--primary-border); color: var(--text); background: var(--primary-fill); }
.knowledge-base-item[aria-pressed='true'] > .el-icon { color: var(--primary); }
.knowledge-base-item:disabled { cursor: default; opacity: 0.6; }
.knowledge-base-summary { display: flex; flex: 1; flex-direction: column; gap: 4px; min-width: 0; }
.knowledge-base-name { display: flex; align-items: baseline; flex-wrap: wrap; gap: 4px 8px; font-size: 14px; font-weight: 500; line-height: 1.5; overflow-wrap: anywhere; }
.common-base-label { flex-shrink: 0; color: var(--primary); font-size: 11px; font-weight: 400; }
.knowledge-base-description { display: -webkit-box; overflow: hidden; color: var(--text-muted); font-size: 12px; line-height: 1.6; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow-wrap: anywhere; }
.common-base + .knowledge-base-item:not(.common-base) { margin-top: 12px; }
.library-placeholder { margin: 0; padding: 12px 8px; color: var(--text-muted); font-size: 13px; line-height: 1.7; }
.create-base-action { flex-shrink: 0; min-height: 36px; margin: 0; }
.library-sidebar-footer { flex-shrink: 0; padding-top: 12px; border-top: 1px solid var(--line); }
.online-links-entry { display: flex; align-items: center; gap: 12px; width: 100%; min-height: 36px; padding: 8px 12px; border: 0; border-radius: 8px; color: var(--text-soft); background: transparent; font-size: 13px; text-align: left; cursor: pointer; }
.online-links-entry[aria-pressed='true'] { color: var(--primary); background: var(--primary-fill); }
.online-links-entry > .el-icon { font-size: 16px; }
.online-link-count { margin-left: auto; color: var(--text-muted); font-size: 12px; font-variant-numeric: tabular-nums; }
.library-sidebar-footer p { margin: 4px 12px 0; color: var(--text-muted); font-size: 12px; line-height: 1.6; }
.base-error { margin: 0; padding: 8px; color: var(--danger-text); font-size: 13px; line-height: 1.7; overflow-wrap: anywhere; }
.base-error button { border: 0; color: var(--primary); background: none; cursor: pointer; }
.knowledge-library-content, .knowledge-documents, .knowledge-links { display: flex; flex: 1; flex-direction: column; min-width: 0; min-height: 0; overflow: hidden; }
.knowledge-base-header { display: flex; flex-shrink: 0; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 16px; min-height: 92px; padding: 20px 24px; border-bottom: 1px solid var(--line); }
.knowledge-base-heading { flex: 1; min-width: 160px; }
.knowledge-base-heading h2 { margin: 0 0 8px; color: var(--text); font-size: 20px; font-weight: 500; line-height: 1.4; overflow-wrap: anywhere; }
.base-scope { display: flex; align-items: center; gap: 8px; color: var(--text-muted); font-size: 12px; line-height: 1.6; }
.scope-indicator { width: 6px; height: 6px; flex-shrink: 0; border-radius: 50%; background: var(--primary); }
.base-description { margin: 8px 0 0; color: var(--text-soft); font-size: 13px; line-height: 1.6; }
.base-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.base-actions .el-button { margin: 0; }
.base-actions > .el-button:not(.page-primary-action) { padding-right: 8px; padding-left: 8px; border-color: transparent; background: transparent; color: var(--text-muted); }
.base-actions > .el-button:not(.page-primary-action):hover { color: var(--text); background: var(--surface-hover); }
.base-actions > .delete-base-action:hover { color: var(--danger-text) !important; }
.base-actions > .page-primary-action { margin-left: 4px; }
.knowledge-workspace { display: grid; flex: 1; grid-template-columns: 288px minmax(0, 1fr); min-height: 0; overflow: hidden; }
.knowledge-workspace.is-empty { grid-template-columns: minmax(0, 1fr); }
.knowledge-workspace.is-empty .knowledge-catalog { display: none; }
.knowledge-catalog { display: flex; flex-direction: column; min-width: 0; min-height: 0; border-right: 1px solid var(--line); background: var(--surface-subtle); }
.catalog-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 16px 16px 12px; }
.catalog-header > div, .search-results > header > div { display: flex; align-items: baseline; flex-wrap: wrap; gap: 4px 12px; }
.catalog-header strong, .search-results > header strong { color: var(--text); font-size: 13px; font-weight: 500; }
.catalog-header span, .search-results > header span { color: var(--text-muted); font-size: 12px; font-variant-numeric: tabular-nums; }
.search-form { display: flex; gap: 8px; padding: 0 16px 16px; }
.search-form .el-input { flex: 1; min-width: 0; }
.search-form .el-button { margin: 0; padding: 0 12px; }
.catalog-body { flex: 1; min-height: 0; overflow-y: auto; scrollbar-gutter: stable; }
.catalog-load-error { padding: 8px 16px 16px; font-size: 13px; line-height: 1.7; }
.catalog-load-error p { color: var(--danger-text); }
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
.knowledge-empty-reader h3 { margin: 0; font-size: 20px; font-weight: 500; }
.knowledge-empty-reader p { margin: 0; color: var(--text-soft); font-size: 14px; line-height: 1.8; }
.knowledge-empty-reader > span { margin-top: 8px; color: var(--text-muted); font-size: 12px; }
button:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
.knowledge-links > .knowledge-link-panel { flex: 1; min-height: 0; padding: 20px 24px; overflow-y: auto; }
@container workspace (max-width: 1100px) {
  .knowledge-workspace { grid-template-columns: minmax(0, 1fr); }
  .knowledge-catalog { border-right: 0; }
  .knowledge-empty-reader, .has-document .knowledge-catalog { display: none; }
  .is-empty .knowledge-empty-reader { display: flex; }
  .knowledge-base-header { padding: 16px 20px; }
  .knowledge-base-heading { flex-basis: 100%; }
}
@container workspace (max-width: 700px) {
  .knowledge-page { overflow-y: auto; }
  .knowledge-header { margin-bottom: 16px; }
  .knowledge-library-layout { flex: none; grid-template-columns: minmax(0, 1fr); min-height: 640px; overflow: visible; }
  .knowledge-library-sidebar { gap: 12px; padding: 16px; border-right: 0; border-bottom: 1px solid var(--line); }
  .knowledge-base-list { display: grid; flex: none; grid-template-columns: repeat(2, minmax(0, 1fr)); align-content: start; gap: 8px; max-height: 256px; }
  .knowledge-base-item { min-height: 76px; padding: 12px; border-color: var(--line); background: var(--surface); }
  .knowledge-base-item + .knowledge-base-item, .common-base + .knowledge-base-item:not(.common-base) { margin-top: 0; }
  .library-placeholder, .base-error { grid-column: 1 / -1; }
  .knowledge-base-item > .el-icon { display: none; }
  .library-sidebar-footer { padding-top: 0; border-top: 0; }
  .library-sidebar-footer p { display: none; }
  .knowledge-library-content { min-height: 400px; }
  .knowledge-base-header { gap: 12px; padding: 16px; }
  .base-actions { gap: 4px; width: 100%; }
  .base-actions > .page-primary-action { margin-left: auto; }
  .knowledge-workspace { min-height: 320px; }
  .knowledge-empty-reader { padding: 24px; }
  .knowledge-empty-reader h3 { font-size: 18px; }
  .knowledge-links > .knowledge-link-panel { padding: 16px; }
}

.knowledge-page.is-reading { padding: 16px 24px; overflow: hidden; }
.is-reading .knowledge-header,
.is-reading .knowledge-library-sidebar,
.is-reading .knowledge-base-header,
.is-reading .knowledge-catalog { display: none; }
.is-reading .knowledge-library-layout {
  flex: 1;
  grid-template-columns: minmax(0, 1fr);
  grid-template-rows: minmax(0, 1fr);
  min-height: 0;
  overflow: hidden;
}
.is-reading .knowledge-library-content { min-height: 0; }
.is-reading .knowledge-workspace {
  grid-template-columns: minmax(0, 1fr);
  grid-template-rows: minmax(0, 1fr);
  min-height: 0;
}
@media (max-width: 760px) {
  .knowledge-page.is-reading { padding: 64px 12px 12px; }
}
</style>
