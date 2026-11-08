<script setup lang="ts">
import {
  Delete,
  Document,
  Download,
  Link,
  Plus,
  Refresh,
  UploadFilled,
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { onMounted, ref } from 'vue';

import {
  deleteKnowledgeDocument,
  deletePublicKnowledgeDocument,
  downloadKnowledgeDocument,
  downloadPublicKnowledgeDocument,
  listKnowledgeDocuments,
  listPublicKnowledgeDocuments,
  uploadKnowledgeDocuments,
  uploadPublicKnowledgeDocuments,
} from '@/api/knowledge';
import KnowledgeLinkPanel from '@/components/knowledge/KnowledgeLinkPanel.vue';
import { useAdministrationStore } from '@/stores/administration';
import type { KnowledgeDocument } from '@/types/knowledge';

const activeResource = ref<'file' | 'link'>('file');
const activeScope = ref<'user' | 'public'>('user');
const administration = useAdministrationStore();
const documents = ref<KnowledgeDocument[]>([]);
const linkCount = ref(0);
const linkPanel = ref<InstanceType<typeof KnowledgeLinkPanel>>();
const loadingDocuments = ref(false);
const uploading = ref(false);
const fileInput = ref<HTMLInputElement>();

async function loadDocuments(): Promise<void> {
  loadingDocuments.value = true;
  try {
    documents.value = await (activeScope.value === 'public'
      ? listPublicKnowledgeDocuments()
      : listKnowledgeDocuments());
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '文件知识库加载失败');
  } finally {
    loadingDocuments.value = false;
  }
}

function chooseFiles(): void {
  fileInput.value?.click();
}

async function uploadFiles(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files || []);
  input.value = '';
  if (!files.length) return;
  uploading.value = true;
  try {
    await (activeScope.value === 'public'
      ? uploadPublicKnowledgeDocuments(files)
      : uploadKnowledgeDocuments(files));
    ElMessage.success(`已上传 ${files.length} 个文档`);
    await loadDocuments();
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
  try {
    await ElMessageBox.confirm(`确定删除“${document.name}”吗？`, '删除文档', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    });
    await (activeScope.value === 'public'
      ? deletePublicKnowledgeDocument(document.path)
      : deleteKnowledgeDocument(document.path));
    ElMessage.success('文档已删除');
    await loadDocuments();
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '删除失败');
    }
  }
}

function formatSize(size: number): string {
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

function formatTime(value: string): string {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '--';
}

async function changeScope(scope: 'user' | 'public'): Promise<void> {
  activeScope.value = scope;
  linkCount.value = 0;
  await loadDocuments();
}

onMounted(async () => {
  try {
    await administration.load();
  } catch {
    // 管理员状态不可用时保持普通用户界面，后端仍会拒绝公共写操作。
  }
  await loadDocuments();
});
</script>

<template>
  <section class="workspace-page knowledge-page">
    <header class="page-header knowledge-header">
      <div>
        <p>Knowledge</p>
        <h1>知识库</h1>
        <span>管理{{ activeScope === 'public' ? '平台公共' : '你的个人' }}文件与在线文档，按文件夹整理知识资料。</span>
      </div>
      <el-button
        v-if="activeResource === 'file'"
        class="page-primary-action"
        type="primary"
        :loading="uploading"
        @click="chooseFiles"
      >
        <el-icon><UploadFilled /></el-icon>
        上传文档
      </el-button>
      <el-button v-else class="page-primary-action" type="primary" @click="linkPanel?.openCreateLink()">
        <el-icon><Plus /></el-icon>
        添加在线文档
      </el-button>
      <input ref="fileInput" class="hidden-file-input" type="file" multiple @change="uploadFiles" />
    </header>

    <div v-if="administration.isAdministrator" class="scope-switch">
      <button :class="{ active: activeScope === 'user' }" @click="changeScope('user')">个人知识库</button>
      <button :class="{ active: activeScope === 'public' }" @click="changeScope('public')">
        公共知识库 · 管理员
      </button>
    </div>

    <div class="knowledge-tabs">
      <button :class="{ active: activeResource === 'file' }" @click="activeResource = 'file'">
        <el-icon><Document /></el-icon>
        本地文件
        <span>{{ documents.length }}</span>
      </button>
      <button :class="{ active: activeResource === 'link' }" @click="activeResource = 'link'">
        <el-icon><Link /></el-icon>
        在线文档
        <span>{{ linkCount }}</span>
      </button>
    </div>

    <div v-if="activeResource === 'file'" class="resource-toolbar">
      <div>
        <strong>{{ documents.length }}</strong>
        <span>个文件</span>
      </div>
      <p v-if="activeScope === 'public'">文件将只读挂载给所有用户的 Docker Sandbox。</p>
      <p v-else>文件保存在当前用户的共享空间，仅当前用户可见。</p>
      <el-button text :loading="loadingDocuments" @click="loadDocuments">
        <el-icon><Refresh /></el-icon>
        刷新
      </el-button>
    </div>

    <div v-if="activeResource === 'file'" v-loading="loadingDocuments" class="resource-list">
      <article v-for="document in documents" :key="document.path" class="resource-row">
        <div class="resource-file-icon"><el-icon><Document /></el-icon></div>
        <div class="resource-main">
          <strong>{{ document.name }}</strong>
          <span>{{ document.path }}</span>
        </div>
        <div class="resource-meta">
          <span>{{ formatSize(document.size) }}</span>
          <span>{{ formatTime(document.updateTime) }}</span>
        </div>
        <div class="resource-actions">
          <el-button text title="下载" @click="downloadDocument(document)">
            <el-icon><Download /></el-icon>
          </el-button>
          <el-button text title="删除" class="danger-action" @click="removeDocument(document)">
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
      </article>
      <el-empty v-if="!loadingDocuments && !documents.length" description="还没有文档，上传一份资料开始使用" />
    </div>

    <KnowledgeLinkPanel
      v-show="activeResource === 'link'"
      ref="linkPanel"
      :public-mode="activeScope === 'public'"
      @count-change="linkCount = $event"
    />
  </section>
</template>
