<script setup lang="ts">
import { Clock, Delete, EditPen, TopRight } from '@element-plus/icons-vue';
import { ElMessageBox } from 'element-plus';
import { onBeforeUnmount, onMounted, ref } from 'vue';

import { deleteAgentApp, listAgentApps } from '@/api/agent-app';
import type { AgentApp } from '@/types/agent-app';

const applications = ref<AgentApp[]>([]);
const loading = ref(true);
const error = ref('');
const actionError = ref('');
const removingId = ref('');
let disposed = false;

async function loadApplications(): Promise<void> {
  loading.value = true;
  error.value = '';
  try { applications.value = await listAgentApps(); }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '应用列表加载失败'; }
  finally { loading.value = false; }
}

async function removeApplication(application: AgentApp): Promise<void> {
  if (removingId.value) return;
  removingId.value = application.id;
  actionError.value = '';
  try {
    await ElMessageBox.confirm(`删除“${application.name}”及其版本和应用数据？`, '删除应用', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消',
    });
    if (disposed) return;
    if (!await deleteAgentApp(application.id)) throw new Error('删除失败，请刷新后重试');
    if (!disposed) applications.value = applications.value.filter(item => item.id !== application.id);
  } catch (cause) {
    if (!disposed && cause !== 'cancel' && cause !== 'close') {
      actionError.value = cause instanceof Error ? cause.message : '删除失败';
    }
  } finally {
    if (!disposed) removingId.value = '';
  }
}

onMounted(loadApplications);
onBeforeUnmount(() => { disposed = true; });
</script>

<template>
  <section class="workspace-page apps-page">
    <header class="page-header">
      <div><p>Agent apps</p><h1>智能应用</h1><span>查看和使用你的个人应用</span></div>
    </header>
    <section class="application-section" aria-labelledby="personal-apps-heading">
      <h2 id="personal-apps-heading">我的应用</h2>
      <p v-if="actionError" role="alert" class="apps-error">{{ actionError }}</p>
      <p v-if="loading" role="status">正在加载应用…</p>
      <div v-else-if="error" role="alert" class="apps-error">{{ error }}<button type="button" @click="loadApplications">重新加载</button></div>
      <div v-else-if="applications.length" class="apps-grid">
        <article v-for="application in applications" :key="application.id" class="app-card">
          <div class="app-art" aria-hidden="true">{{ application.name.slice(0, 1) }}</div>
          <div class="app-info">
            <h3><RouterLink :to="`/apps/${encodeURIComponent(application.id)}`" class="app-link">
              {{ application.name }}<el-icon class="app-arrow" aria-hidden="true"><TopRight /></el-icon>
            </RouterLink></h3>
            <p>{{ application.description }}</p>
          </div>
          <div class="app-actions" role="group" :aria-label="`${application.name}管理操作`">
            <button type="button" class="app-action delete-action" :aria-label="`删除${application.name}`"
              :disabled="!!removingId" @click.stop="removeApplication(application)">
              <el-icon aria-hidden="true"><Delete /></el-icon>{{ removingId === application.id ? '删除中…' : '删除' }}
            </button>
            <RouterLink :to="`/apps/${encodeURIComponent(application.id)}?panel=edit`" class="app-action"
              :aria-label="`编辑${application.name}`"><el-icon aria-hidden="true"><EditPen /></el-icon>编辑</RouterLink>
            <RouterLink :to="`/apps/${encodeURIComponent(application.id)}?panel=versions`" class="app-action"
              :aria-label="`${application.name}版本记录`"><el-icon aria-hidden="true"><Clock /></el-icon>版本记录</RouterLink>
          </div>
        </article>
      </div>
      <p v-else class="empty-message">暂无智能应用</p>
    </section>
  </section>
</template>

<style scoped>
.apps-page { min-width: 0; }
.application-section { width: min(var(--workspace-max-width), 100%); margin: 0 auto 32px; }
button { padding: 10px 16px; border: 1px solid var(--line); border-radius: 8px; background: var(--surface); color: var(--text); cursor: pointer; }
button:focus-visible { outline: 2px solid var(--text-accent); outline-offset: 2px; }
.empty-message { margin: 12px 0 0; color: var(--text-muted); font-size: 12px; line-height: 1.7; }
h2 { margin: 0 0 16px; font-size: 18px; font-weight: 500; }
.apps-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(280px, 100%), 1fr)); gap: 24px; }
.app-card { position: relative; background: var(--surface); border: 1px solid var(--line); border-radius: 12px; overflow: hidden; }
.app-card:hover, .app-card:focus-within { border-color: var(--line-strong); }
.app-art { display: flex; height: 144px; align-items: center; justify-content: center; font-size: 40px; color: var(--text-accent); background: var(--surface-soft); }
.app-info { padding: 24px 24px 64px; }
h3 { font-size: 17px; font-weight: 500; margin: 0 0 12px; }
.app-info p { color: var(--text-soft); font-size: 13px; line-height: 1.8; margin: 0; }
.app-link { display: flex; align-items: center; justify-content: space-between; gap: 16px; color: inherit; text-decoration: none; }
.app-link::after { content: ''; position: absolute; inset: 0; border-radius: 12px; }
.app-link:focus-visible { outline: none; }
.app-link:focus-visible::after { outline: 2px solid var(--text-accent); outline-offset: -4px; }
.app-arrow { flex-shrink: 0; color: var(--text-muted); font-size: 20px; }
.app-actions { position: absolute; right: 16px; bottom: 12px; z-index: 1; display: flex; gap: 4px; opacity: 0; visibility: hidden; pointer-events: none; transition: opacity 120ms ease; }
.app-card:hover .app-actions, .app-card:focus-within .app-actions { opacity: 1; visibility: visible; pointer-events: auto; }
.app-action { display: inline-flex; align-items: center; gap: 4px; padding: 6px 8px; border: 0; border-radius: 6px; background: transparent; color: var(--text-muted); font-size: 12px; line-height: 1.5; text-decoration: none; cursor: pointer; }
.app-action:hover { background: var(--surface-soft); color: var(--text); }
.app-action:focus-visible { outline: 2px solid var(--text-accent); outline-offset: 2px; }
.delete-action:hover { color: var(--danger-text); }
.app-action:disabled { opacity: .5; cursor: default; }
.apps-error { color: var(--danger-text); font-size: 13px; line-height: 1.8; }
.apps-error button { margin-left: 12px; }
@media (hover: none) { .app-actions { opacity: 1; visibility: visible; pointer-events: auto; } }
</style>
