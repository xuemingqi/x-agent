<script setup lang="ts">
import {
  ArrowRight,
  Delete,
  EditPen,
  Folder,
  FolderAdd,
  FolderOpened,
  Link,
  Refresh,
  TopRight,
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';

import {
  createKnowledgeLink,
  createKnowledgeLinkFolder,
  createPublicKnowledgeLink,
  createPublicKnowledgeLinkFolder,
  deleteKnowledgeLink,
  deleteKnowledgeLinkFolder,
  deletePublicKnowledgeLink,
  deletePublicKnowledgeLinkFolder,
  listKnowledgeLinkFolders,
  listKnowledgeLinks,
  listPublicKnowledgeLinkFolders,
  listPublicKnowledgeLinks,
  renameKnowledgeLinkFolder,
  renamePublicKnowledgeLinkFolder,
  updateKnowledgeLink,
  updatePublicKnowledgeLink,
} from '@/api/knowledge';
import { KNOWLEDGE_LINK_SCOPE, KNOWLEDGE_ROOT_FOLDER_ID } from '@/types/knowledge';
import type { KnowledgeLink, KnowledgeLinkFolder, KnowledgeLinkPayload } from '@/types/knowledge';
import { buildTree, flattenExpandedTree, getTreePath } from '@/utils/tree';

type KnowledgeResource = { id: string; parentId: string } & (
  { kind: 'folder'; folder: KnowledgeLinkFolder } | { kind: 'link'; link: KnowledgeLink }
);

const FOLDER_INDENT_PX = 20;
const MIN_RESOURCE_CONTENT_WIDTH = 520;
const MAX_INLINE_FOLDER_DEPTH = 3;

const props = withDefaults(defineProps<{ publicMode?: boolean }>(), { publicMode: false });
const emit = defineEmits<{ countChange: [count: number] }>();
const links = ref<KnowledgeLink[]>([]);
const folders = ref<KnowledgeLinkFolder[]>([]);
const loading = ref(false);
const currentFolderId = ref(KNOWLEDGE_ROOT_FOLDER_ID);
const selectedFolderId = ref(KNOWLEDGE_ROOT_FOLDER_ID);
const expandedFolderIds = ref(new Set<string>());
const draggingLink = ref<KnowledgeLink>();
const dropTargetFolderId = ref('');
const movingLinkId = ref('');
const resourceList = ref<HTMLElement>();
const listWidth = ref(800);
let resizeObserver: ResizeObserver | undefined;
const linkDialogVisible = ref(false);
const editingLinkId = ref('');
const savingLink = ref(false);
const linkForm = reactive<KnowledgeLinkPayload>({
  name: '', summary: '', url: '', folderId: KNOWLEDGE_ROOT_FOLDER_ID,
});
const folderDialogVisible = ref(false);
const editingFolderId = ref('');
const savingFolder = ref(false);
const folderForm = reactive({ name: '', parentId: KNOWLEDGE_ROOT_FOLDER_ID });

const maxInlineDepth = computed(() => Math.max(1, Math.min(MAX_INLINE_FOLDER_DEPTH,
  Math.floor((listWidth.value - MIN_RESOURCE_CONTENT_WIDTH) / FOLDER_INDENT_PX))));
const resourceTree = computed(() => buildTree<KnowledgeResource>([
  ...folders.value.map((folder): KnowledgeResource => ({
    id: folder.id, parentId: folder.parentId, kind: 'folder', folder,
  })),
  ...links.value.map((link): KnowledgeResource => ({
    id: `link:${link.id}`, parentId: link.folderId, kind: 'link', link,
  })),
], currentFolderId.value));
const visibleRows = computed(() => flattenExpandedTree(resourceTree.value, expandedFolderIds.value, maxInlineDepth.value));
const breadcrumbs = computed(() => getTreePath(folders.value, currentFolderId.value, KNOWLEDGE_ROOT_FOLDER_ID));
const hiddenBreadcrumbs = computed(() => breadcrumbs.value.length > 3 ? breadcrumbs.value.slice(0, -2) : []);
const visibleBreadcrumbs = computed(() => hiddenBreadcrumbs.value.length ? breadcrumbs.value.slice(-2) : breadcrumbs.value);
const currentFolderPath = computed(() => formatFolderPath(currentFolderId.value));
const folderParentPath = computed(() => formatFolderPath(folderForm.parentId));
const folderOptions = computed(() => [{
  id: KNOWLEDGE_ROOT_FOLDER_ID,
  name: `${props.publicMode ? '公共' : '个人'}知识库 / 根目录`,
  children: buildTree(folders.value.filter((folder) => folder.editable), KNOWLEDGE_ROOT_FOLDER_ID),
}]);

async function loadResources(): Promise<void> {
  if (loading.value) return;
  loading.value = true;
  try {
    const [loadedLinks, loadedFolders] = await Promise.all(props.publicMode
      ? [listPublicKnowledgeLinks(), listPublicKnowledgeLinkFolders()]
      : [listKnowledgeLinks(), listKnowledgeLinkFolders()]);
    const expectedScope = props.publicMode ? KNOWLEDGE_LINK_SCOPE.PUBLIC : KNOWLEDGE_LINK_SCOPE.USER;
    links.value = loadedLinks.filter((link) => link.scope === expectedScope);
    folders.value = loadedFolders.filter((folder) => folder.scope === expectedScope);
    emit('countChange', links.value.length);
    const folderIds = new Set(folders.value.map((folder) => folder.id));
    expandedFolderIds.value = new Set([...expandedFolderIds.value].filter((id) => folderIds.has(id)));
    if (currentFolderId.value !== KNOWLEDGE_ROOT_FOLDER_ID && !breadcrumbs.value.length) {
      focusFolder(KNOWLEDGE_ROOT_FOLDER_ID);
    }
    if (selectedFolderId.value !== KNOWLEDGE_ROOT_FOLDER_ID && !folderIds.has(selectedFolderId.value)) {
      selectedFolderId.value = currentFolderId.value;
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '在线知识库加载失败');
  } finally {
    loading.value = false;
  }
}

function focusFolder(id: string): void {
  currentFolderId.value = id;
  selectedFolderId.value = id;
  expandedFolderIds.value = new Set();
}

function toggleFolder(folder: KnowledgeLinkFolder, depth: number): void {
  if (depth >= maxInlineDepth.value) {
    focusFolder(folder.id);
    return;
  }
  selectedFolderId.value = folder.id;
  if (expandedFolderIds.value.has(folder.id)) {
    expandedFolderIds.value.delete(folder.id);
  } else {
    expandedFolderIds.value.add(folder.id);
  }
}

/** 保存后展开目标目录；目标过深时以其为面包屑起点，保持文档可见。 */
function revealFolder(id: string): void {
  const path = getTreePath(folders.value, id, KNOWLEDGE_ROOT_FOLDER_ID);
  const currentIndex = path.findIndex((folder) => folder.id === currentFolderId.value);
  const relativePath = currentFolderId.value === KNOWLEDGE_ROOT_FOLDER_ID ? path : path.slice(currentIndex + 1);
  if ((currentFolderId.value !== KNOWLEDGE_ROOT_FOLDER_ID && currentIndex < 0) || relativePath.length > maxInlineDepth.value) {
    focusFolder(id);
    return;
  }
  selectedFolderId.value = id;
  relativePath.forEach((folder) => expandedFolderIds.value.add(folder.id));
}

function openCreateLink(): void {
  if (loading.value) return;
  editingLinkId.value = '';
  Object.assign(linkForm, {
    name: '', summary: '', url: '',
    folderId: selectedFolderId.value,
  });
  linkDialogVisible.value = true;
}

function openEditLink(link: KnowledgeLink): void {
  editingLinkId.value = link.id;
  Object.assign(linkForm, { name: link.name, summary: link.summary, url: link.url, folderId: link.folderId });
  linkDialogVisible.value = true;
}

async function saveLink(): Promise<void> {
  if (savingLink.value) return;
  const payload: KnowledgeLinkPayload = {
    name: linkForm.name.trim(), summary: linkForm.summary.trim(), url: linkForm.url.trim(), folderId: linkForm.folderId,
  };
  if (!payload.name || !payload.summary || !payload.url || !payload.folderId) {
    ElMessage.warning('请填写完整的在线文档信息并选择文件夹');
    return;
  }
  savingLink.value = true;
  try {
    if (editingLinkId.value) {
      await (props.publicMode ? updatePublicKnowledgeLink : updateKnowledgeLink)(editingLinkId.value, payload);
    } else {
      await (props.publicMode ? createPublicKnowledgeLink : createKnowledgeLink)(payload);
    }
    ElMessage.success(editingLinkId.value ? '在线文档已更新'
      : `在线文档已添加到${props.publicMode ? '公共' : '个人'}知识库`);
    linkDialogVisible.value = false;
    await loadResources();
    revealFolder(payload.folderId);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败');
  } finally {
    savingLink.value = false;
  }
}

async function removeLink(link: KnowledgeLink): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除在线文档“${link.name}”吗？`, '删除在线文档', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning',
    });
    await (props.publicMode ? deletePublicKnowledgeLink : deleteKnowledgeLink)(link.id);
    ElMessage.success('在线文档已删除');
    await loadResources();
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '删除失败');
    }
  }
}

function startResourceDrag(event: DragEvent, resource: KnowledgeResource): void {
  if (resource.kind !== 'link' || !resource.link.editable || movingLinkId.value) {
    event.preventDefault();
    return;
  }
  draggingLink.value = resource.link;
  dropTargetFolderId.value = '';
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move';
    event.dataTransfer.setData('text/plain', resource.link.id);
  }
}

function finishLinkDrag(): void {
  draggingLink.value = undefined;
  dropTargetFolderId.value = '';
}

function showLinkDropTarget(event: DragEvent, folderId: string, editable = true): void {
  if (!draggingLink.value) return;
  if (!editable || draggingLink.value.folderId === folderId) {
    dropTargetFolderId.value = '';
    return;
  }
  event.preventDefault();
  dropTargetFolderId.value = folderId;
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move';
}

function showResourceDropTarget(event: DragEvent, resource: KnowledgeResource): void {
  if (resource.kind === 'folder') {
    showLinkDropTarget(event, resource.folder.id, resource.folder.editable);
  } else if (draggingLink.value) {
    dropTargetFolderId.value = '';
  }
}

async function moveDraggedLink(event: DragEvent, folderId: string, editable = true): Promise<void> {
  const link = draggingLink.value;
  if (!editable || !link || link.folderId === folderId || movingLinkId.value) return;
  event.preventDefault();
  movingLinkId.value = link.id;
  finishLinkDrag();
  try {
    await (props.publicMode ? updatePublicKnowledgeLink : updateKnowledgeLink)(link.id, {
      name: link.name, summary: link.summary, url: link.url, folderId,
    });
    ElMessage.success(`“${link.name}”已移动到${formatFolderPath(folderId)}`);
    await loadResources();
    revealFolder(folderId);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '文档移动失败');
  } finally {
    movingLinkId.value = '';
  }
}

function moveDraggedResource(event: DragEvent, resource: KnowledgeResource): void {
  if (resource.kind === 'folder') void moveDraggedLink(event, resource.folder.id, resource.folder.editable);
}

function openCreateFolder(parentId: string): void {
  if (loading.value) return;
  editingFolderId.value = '';
  Object.assign(folderForm, { name: '', parentId });
  folderDialogVisible.value = true;
}

function openRenameFolder(folder: KnowledgeLinkFolder): void {
  editingFolderId.value = folder.id;
  Object.assign(folderForm, { name: folder.name, parentId: folder.parentId });
  folderDialogVisible.value = true;
}

async function saveFolder(): Promise<void> {
  if (savingFolder.value) return;
  const name = folderForm.name.trim();
  if (!name) {
    ElMessage.warning('请填写文件夹名称');
    return;
  }
  savingFolder.value = true;
  let createdFolderId = '';
  try {
    if (editingFolderId.value) {
      await (props.publicMode ? renamePublicKnowledgeLinkFolder : renameKnowledgeLinkFolder)(editingFolderId.value, name);
    } else {
      const createFolder = props.publicMode ? createPublicKnowledgeLinkFolder : createKnowledgeLinkFolder;
      const createdFolder = await createFolder({ name, parentId: folderForm.parentId });
      createdFolderId = createdFolder.id;
    }
    ElMessage.success(editingFolderId.value ? '文件夹已重命名' : '文件夹已创建');
    folderDialogVisible.value = false;
    await loadResources();
    if (createdFolderId) revealFolder(createdFolderId);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '文件夹保存失败');
  } finally {
    savingFolder.value = false;
  }
}

async function removeFolder(folder: KnowledgeLinkFolder): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除文件夹“${folder.name}”吗？仅允许删除空文件夹，不会连带删除文档。`, '删除文件夹', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning',
    });
    await (props.publicMode ? deletePublicKnowledgeLinkFolder : deleteKnowledgeLinkFolder)(folder.id);
    ElMessage.success('文件夹已删除');
    await loadResources();
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '文件夹删除失败');
    }
  }
}

function openLink(link: KnowledgeLink): void {
  window.open(link.url, '_blank', 'noopener,noreferrer');
}

function formatTime(value: string): string {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '--';
}

function formatFolderPath(id: string): string {
  const rootName = props.publicMode ? '公共知识库' : '个人知识库';
  if (id === KNOWLEDGE_ROOT_FOLDER_ID) return `${rootName} / 根目录`;
  const path = getTreePath(folders.value, id, KNOWLEDGE_ROOT_FOLDER_ID);
  return [rootName, ...path.map((folder) => folder.name)].join(' / ');
}

defineExpose({ openCreateLink, loadResources });
onMounted(() => {
  void loadResources();
  resizeObserver = new ResizeObserver(([entry]) => {
    if (entry && entry.contentRect.width > 0) listWidth.value = entry.contentRect.width;
  });
  if (resourceList.value) resizeObserver.observe(resourceList.value);
});
onBeforeUnmount(() => resizeObserver?.disconnect());
watch(() => props.publicMode, () => {
  focusFolder(KNOWLEDGE_ROOT_FOLDER_ID);
  void loadResources();
});
</script>

<template>
  <div class="knowledge-link-panel">
    <div class="resource-toolbar">
      <div><strong>{{ links.length }}</strong><span>个{{ publicMode ? '公共' : '个人' }}文档</span></div>
      <p>点击文件夹原地展开；拖动文档到文件夹可快速移动。</p>
      <el-button title="在根目录新建文件夹" :disabled="loading" @click="openCreateFolder(KNOWLEDGE_ROOT_FOLDER_ID)">
        <el-icon><FolderAdd /></el-icon>新建根文件夹
      </el-button>
      <el-button text :loading="loading" @click="loadResources"><el-icon><Refresh /></el-icon>刷新</el-button>
    </div>

    <nav class="knowledge-folder-path" aria-label="当前文件夹">
      <button
        class="knowledge-root-folder-entry"
        :class="{ 'folder-drop-target': dropTargetFolderId === KNOWLEDGE_ROOT_FOLDER_ID }"
        :aria-current="currentFolderId === KNOWLEDGE_ROOT_FOLDER_ID ? 'page' : undefined"
        title="可将文档拖到这里移至根目录"
        @click="focusFolder(KNOWLEDGE_ROOT_FOLDER_ID)"
        @dragenter.prevent="showLinkDropTarget($event, KNOWLEDGE_ROOT_FOLDER_ID)"
        @dragover.prevent="showLinkDropTarget($event, KNOWLEDGE_ROOT_FOLDER_ID)"
        @drop.prevent.stop="moveDraggedLink($event, KNOWLEDGE_ROOT_FOLDER_ID)"
      >
        {{ publicMode ? '公共知识库' : '个人知识库' }} / 根目录
      </button>
      <template v-if="hiddenBreadcrumbs.length">
        <span aria-hidden="true">/</span>
        <el-dropdown @command="focusFolder">
          <button aria-label="展开上级文件夹路径">…</button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="folder in hiddenBreadcrumbs" :key="folder.id" :command="folder.id">{{ folder.name }}</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </template>
      <template v-for="folder in visibleBreadcrumbs" :key="folder.id">
        <span aria-hidden="true">/</span>
        <button
          :class="{ 'folder-drop-target': dropTargetFolderId === folder.id }"
          :title="draggingLink ? `将文档移动到${folder.name}` : folder.name"
          :aria-current="currentFolderId === folder.id ? 'page' : undefined"
          @click="focusFolder(folder.id)"
          @dragenter="showLinkDropTarget($event, folder.id, folder.editable)"
          @dragover="showLinkDropTarget($event, folder.id, folder.editable)"
          @drop="moveDraggedLink($event, folder.id, folder.editable)"
        >{{ folder.name }}</button>
      </template>
      <span class="knowledge-folder-count" :title="currentFolderPath">当前目录：{{ currentFolderPath }}</span>
    </nav>

    <div ref="resourceList" v-loading="loading" class="resource-list knowledge-resource-list">
      <div class="knowledge-list-heading" aria-hidden="true"><span>名称 / 简介</span><span>操作</span></div>
      <ul :aria-label="`${publicMode ? '公共' : '个人'}在线文档`">
        <li
          v-for="row in visibleRows" :key="row.node.id" class="resource-row knowledge-resource-row"
          :class="{
            'selected-folder': row.node.kind === 'folder' && row.node.id === selectedFolderId,
            'folder-drop-target': row.node.kind === 'folder' && row.node.id === dropTargetFolderId,
            'draggable-link': row.node.kind === 'link' && row.node.link.editable,
            'dragging-link': row.node.kind === 'link' && row.node.link.id === draggingLink?.id,
            'moving-link': row.node.kind === 'link' && row.node.link.id === movingLinkId,
          }"
          :draggable="row.node.kind === 'link' && row.node.link.editable && !movingLinkId"
          :style="{ '--resource-indent': `${row.depth * FOLDER_INDENT_PX}px` }"
          @dragstart="startResourceDrag($event, row.node)"
          @dragend="finishLinkDrag"
          @dragenter="showResourceDropTarget($event, row.node)"
          @dragover="showResourceDropTarget($event, row.node)"
          @drop="moveDraggedResource($event, row.node)"
        >
          <template v-if="row.node.kind === 'folder'">
            <button
              class="knowledge-folder-entry"
              :aria-label="`${row.depth >= maxInlineDepth ? '浏览' : expandedFolderIds.has(row.node.id) ? '收起' : '展开'}文件夹 ${row.node.folder.name}`"
              :aria-expanded="row.depth < maxInlineDepth ? expandedFolderIds.has(row.node.id) : undefined"
              @click="toggleFolder(row.node.folder, row.depth)"
            >
              <el-icon class="folder-chevron" :class="{ expanded: row.depth < maxInlineDepth && expandedFolderIds.has(row.node.id) }"><ArrowRight /></el-icon>
              <el-icon class="folder-symbol"><FolderOpened v-if="expandedFolderIds.has(row.node.id)" /><Folder v-else /></el-icon>
              <span class="resource-main"><strong :title="row.node.folder.name">{{ row.node.folder.name }}</strong></span>
              <span class="folder-detail">{{ row.depth >= maxInlineDepth ? '面包屑浏览' : row.node.children.length ? `${row.node.children.length} 项` : '空文件夹' }}</span>
            </button>
            <div v-if="row.node.folder.editable" class="resource-actions">
              <el-button text title="新建子文件夹" :disabled="loading" @click="openCreateFolder(row.node.id)"><el-icon><FolderAdd /></el-icon></el-button>
              <el-button text title="重命名文件夹" @click="openRenameFolder(row.node.folder)"><el-icon><EditPen /></el-icon></el-button>
              <el-button text title="删除空文件夹" class="danger-action" @click="removeFolder(row.node.folder)"><el-icon><Delete /></el-icon></el-button>
            </div>
          </template>
          <template v-else>
            <span class="folder-chevron" aria-hidden="true"></span>
            <el-icon class="link-symbol"><Link /></el-icon>
            <div class="resource-main knowledge-link-main">
              <strong :title="row.node.link.name">{{ row.node.link.name }}</strong>
              <p :title="row.node.link.summary">{{ row.node.link.summary }}</p>
              <span :title="row.node.link.url">{{ row.node.link.url }}</span>
            </div>
            <div class="resource-meta"><span>更新于</span><span>{{ formatTime(row.node.link.updateTime) }}</span></div>
            <div class="resource-actions">
              <el-button text title="打开链接" @click="openLink(row.node.link)"><el-icon><TopRight /></el-icon></el-button>
              <el-button v-if="row.node.link.editable" text title="编辑或移动文档" @click="openEditLink(row.node.link)"><el-icon><EditPen /></el-icon></el-button>
              <el-button v-if="row.node.link.editable" text title="删除" class="danger-action" @click="removeLink(row.node.link)"><el-icon><Delete /></el-icon></el-button>
            </div>
          </template>
        </li>
      </ul>
      <el-empty v-if="!loading && !visibleRows.length" description="当前文件夹为空" />
    </div>

    <el-dialog
      v-model="linkDialogVisible"
      class="content-editor-dialog knowledge-editor-dialog"
      width="min(650px, 94vw)"
      align-center
      append-to-body
      destroy-on-close
    >
      <template #header>
        <div class="content-dialog-heading">
          <div class="content-dialog-heading-icon knowledge"><el-icon><Link /></el-icon></div>
          <div>
            <span>{{ publicMode ? 'PUBLIC KNOWLEDGE' : 'PERSONAL KNOWLEDGE' }}</span>
            <strong>{{ editingLinkId ? '编辑在线文档' : '添加在线文档' }}</strong>
            <p>将可信文档链接加入知识库，Agent 会按场景读取内容。</p>
          </div>
        </div>
      </template>

      <el-form class="content-editor-form" label-position="top" @submit.prevent="saveLink">
        <section class="content-form-section">
          <header class="content-form-section-heading">
            <span><el-icon><Link /></el-icon></span>
            <div>
              <strong>文档信息</strong>
              <p>名称和介绍会帮助 Agent 选择正确的知识来源。</p>
            </div>
          </header>

          <div class="form-grid">
            <el-form-item label="所属文件夹" required>
              <el-tree-select
                v-model="linkForm.folderId"
                :data="folderOptions"
                :props="{ label: 'name', value: 'id' }"
                node-key="id"
                check-strictly
                filterable
                default-expand-all
                :render-after-expand="false"
                class="knowledge-folder-select"
              />
            </el-form-item>
            <el-form-item label="文档名称" required>
              <el-input v-model="linkForm.name" maxlength="256" placeholder="例如：司机准入规则">
                <template #prefix><el-icon><Link /></el-icon></template>
              </el-input>
            </el-form-item>
          </div>

          <el-form-item label="简短介绍" required>
            <el-input
              v-model="linkForm.summary"
              type="textarea"
              :rows="3"
              maxlength="1000"
              resize="none"
              show-word-limit
              placeholder="说明文档包含的业务内容，以及应该在什么场景下读取"
            />
          </el-form-item>
        </section>

        <section class="content-form-section knowledge-url-section">
          <header class="content-form-section-heading">
            <span><el-icon><TopRight /></el-icon></span>
            <div>
              <strong>文档地址</strong>
              <p>支持可由 Agent 访问的在线文档。</p>
            </div>
          </header>
          <el-form-item label="完整链接" required>
            <el-input v-model="linkForm.url" maxlength="2048" placeholder="https://...">
              <template #prefix><el-icon><Link /></el-icon></template>
            </el-input>
            <p class="field-help">更改所属文件夹即可移动文档，不会改变原文权限。</p>
          </el-form-item>
        </section>
      </el-form>

      <template #footer>
        <div class="content-dialog-footer">
          <span>{{ publicMode ? '所有用户的 Agent 都可以使用这份文档。' : '仅你的 Agent 可以使用这份文档。' }}</span>
          <div>
            <el-button @click="linkDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="savingLink" @click="saveLink">
              {{ editingLinkId ? '保存修改' : '添加文档' }}
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="folderDialogVisible"
      class="content-editor-dialog folder-editor-dialog"
      width="min(480px, 94vw)"
      align-center
      append-to-body
      destroy-on-close
    >
      <template #header>
        <div class="content-dialog-heading">
          <div class="content-dialog-heading-icon folder"><el-icon><FolderOpened /></el-icon></div>
          <div>
            <span>KNOWLEDGE FOLDER</span>
            <strong>{{ editingFolderId ? '重命名文件夹' : '新建文件夹' }}</strong>
            <p>用清晰的目录组织在线文档。</p>
          </div>
        </div>
      </template>

      <el-form class="content-editor-form" label-position="top" @submit.prevent="saveFolder">
        <section class="content-form-section">
          <el-form-item v-if="!editingFolderId" label="新建位置">
            <div class="knowledge-folder-location">
              <el-icon><FolderOpened /></el-icon>
              <span>{{ folderParentPath }}</span>
            </div>
          </el-form-item>
          <el-form-item label="文件夹名称" required>
            <el-input v-model="folderForm.name" maxlength="256" placeholder="例如：业务规则">
              <template #prefix><el-icon><Folder /></el-icon></template>
            </el-input>
            <p class="field-help">
              {{ editingFolderId ? '重命名不会影响文档归属。' : '可在文件夹右侧继续创建子文件夹。' }}
            </p>
          </el-form-item>
        </section>
      </el-form>

      <template #footer>
        <div class="content-dialog-footer">
          <div>
            <el-button @click="folderDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="savingFolder" @click="saveFolder">
              {{ editingFolderId ? '保存修改' : '创建文件夹' }}
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.knowledge-link-panel {
  width: min(1080px, 100%);
  margin: 0 auto;
}

.knowledge-folder-path {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin: 0 0 14px;
  color: var(--text-muted);
  font-size: 12px;

  button {
    max-width: min(220px, 100%);
    padding: 4px;
    border: 0;
    color: var(--text-muted);
    background: transparent;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    cursor: pointer;

    &[aria-current='page'], &:hover { color: var(--text); }
    &.folder-drop-target { color: var(--text); background: var(--primary-soft); outline: 2px solid var(--primary-bright); }
  }
}

.knowledge-root-folder-entry {
  width: 160px;
  padding: 7px 10px !important;
  border: 1px dashed var(--line) !important;
  border-radius: 8px;
}

.knowledge-folder-count {
  max-width: 260px;
  margin-left: auto;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.knowledge-folder-select { width: 100%; }

.knowledge-resource-list ul { margin: 0; padding: 0; list-style: none; }

.knowledge-list-heading {
  display: flex;
  justify-content: space-between;
  padding: 12px 24px;
  border-bottom: 1px solid var(--line);
  color: var(--text-muted);
  background: var(--surface-subtle);
  font-size: 12px;
}

.knowledge-resource-row {
  min-height: 48px;
  gap: 10px;
  padding: 8px 12px 8px calc(12px + var(--resource-indent));

  &.selected-folder { background: var(--primary-soft); }
  &.folder-drop-target { background: var(--primary-soft); box-shadow: inset 0 0 0 2px var(--primary-bright); }
  &.draggable-link { cursor: grab; }
  &.dragging-link { opacity: 0.45; }
  &.moving-link { opacity: 0.55; pointer-events: none; }
  .resource-actions { flex-shrink: 0; }
  .resource-meta { flex-basis: 150px; }
}

.knowledge-folder-entry {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 10px;
  min-width: 0;
  min-height: 30px;
  padding: 0;
  border: 0;
  color: var(--text);
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.folder-chevron { flex: 0 0 14px; width: 14px; color: var(--text-muted); transition: transform 150ms; }
.folder-chevron.expanded { transform: rotate(90deg); }
.folder-symbol, .link-symbol { flex: 0 0 22px; font-size: 20px; }
.folder-symbol { color: var(--warning); }
.link-symbol { color: var(--success); }
.folder-detail { flex-shrink: 0; color: var(--text-muted); font-size: 11px; }
.knowledge-link-main p { margin: 4px 0 0; -webkit-line-clamp: 1; }
.knowledge-folder-entry:focus-visible, .knowledge-folder-path button:focus-visible {
  outline: 2px solid var(--primary-bright);
  outline-offset: 3px;
  border-radius: 4px;
}

@media (max-width: 760px) {
  .knowledge-resource-row .resource-meta { display: none; }
  .knowledge-resource-row { gap: 6px; }
  .knowledge-folder-entry { gap: 6px; }
  .folder-detail { display: none; }
  .knowledge-folder-count { width: 100%; max-width: 100%; margin-left: 0; }
}
</style>
