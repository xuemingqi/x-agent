<script setup lang="ts">
import { ArrowLeft, RefreshRight } from '@element-plus/icons-vue';
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { activateAgentAppVersion, getAgentApp, listAgentAppVersions, saveAgentApp } from '@/api/agent-app';
import ApplicationRuntime from '@/components/apps/ApplicationRuntime.vue';
import type { AgentAppDetail, AgentAppPermission, AgentAppSave, AgentAppVersion } from '@/types/agent-app';
import { createApplicationChatRoute } from '@/utils/app-chat';
import { compileApplicationSource } from '@/utils/app-compiler';

const route = useRoute();
const router = useRouter();
const application = ref<AgentAppDetail>();
const versions = ref<AgentAppVersion[]>([]);
const loading = ref(true);
const busy = ref(false);
const error = ref('');
const runtimeError = ref('');
const revision = ref(0);
const panel = computed(() => route.query.panel === 'edit' || route.query.panel === 'versions' ? route.query.panel : 'run');
const editorTab = ref<'html' | 'css' | 'source'>('source');
const draft = ref<AgentAppSave>({ name: '', description: '', html: '', css: '', source: '', permissions: [] });
const permissions: { value: AgentAppPermission; label: string }[] = [
  { value: 'model.generate', label: '模型生成' }, { value: 'agent.run', label: 'Agent 执行' }, { value: 'storage', label: '保存应用数据' },
  { value: 'http.request', label: 'HTTP 请求' },
];
let navigationRevision = 0;
const versionLabel = computed(() => application.value?.currentVersion?.slice(0, 8));

function fillDraft(detail: AgentAppDetail): void {
  draft.value = { name: detail.name, description: detail.description, html: detail.content.html,
    css: detail.content.css, source: detail.content.source, permissions: [...detail.content.permissions],
    baseVersion: detail.currentVersion ?? undefined };
}

watch(() => [route.params.appId, panel.value] as const, async ([id, selectedPanel]) => {
  const navigation = ++navigationRevision;
  if (typeof id !== 'string') return;
  busy.value = false;
  error.value = '';
  versions.value = [];
  try {
    if (application.value?.id !== id) {
      loading.value = true;
      application.value = undefined;
      runtimeError.value = '';
      const detail = await getAgentApp(id);
      if (navigation !== navigationRevision) return;
      application.value = detail;
    }
    if (selectedPanel === 'edit') fillDraft(application.value);
    if (selectedPanel === 'versions') {
      busy.value = true;
      const records = await listAgentAppVersions(id);
      if (navigation !== navigationRevision) return;
      versions.value = records;
    }
    if (selectedPanel === 'run') runtimeError.value = '';
  } catch (cause) {
    if (navigation === navigationRevision) {
      error.value = cause instanceof Error ? cause.message : selectedPanel === 'versions' ? '版本加载失败' : '应用加载失败';
    }
  } finally {
    if (navigation === navigationRevision) {
      loading.value = false;
      busy.value = false;
    }
  }
}, { immediate: true });

onBeforeUnmount(() => { navigationRevision++; });

async function save(): Promise<void> {
  if (!application.value || busy.value) return;
  const navigation = navigationRevision;
  const appId = application.value.id;
  const payload = { ...draft.value, permissions: [...draft.value.permissions] };
  busy.value = true;
  error.value = '';
  try {
    await compileApplicationSource({ ...payload, sdkVersion: 1 });
    if (navigation !== navigationRevision) return;
    const detail = await saveAgentApp(payload, appId);
    if (application.value?.id !== appId) return;
    application.value = detail;
    runtimeError.value = '';
    revision.value++;
    if (navigation !== navigationRevision) return;
    fillDraft(detail);
    await returnToApplication();
  } catch (cause) {
    if (navigation === navigationRevision) error.value = cause instanceof Error ? cause.message : '保存失败';
  } finally { if (navigation === navigationRevision) busy.value = false; }
}

async function returnToApplication(): Promise<void> {
  const query = { ...route.query };
  delete query.panel;
  await router.replace({ query });
}

async function activate(version: AgentAppVersion): Promise<void> {
  if (!application.value?.currentVersion || busy.value) return;
  const navigation = navigationRevision;
  const { id, currentVersion } = application.value;
  busy.value = true;
  error.value = '';
  try {
    const detail = await activateAgentAppVersion(id, version.version, currentVersion);
    if (application.value?.id !== id) return;
    application.value = detail;
    runtimeError.value = '';
    revision.value++;
    if (navigation !== navigationRevision) return;
    fillDraft(detail);
    await returnToApplication();
  } catch (cause) { if (navigation === navigationRevision) error.value = cause instanceof Error ? cause.message : '版本恢复失败'; }
  finally { if (navigation === navigationRevision) busy.value = false; }
}

function editWithAgent(): void {
  if (!application.value) return;
  const detail = application.value;
  const prompt = runtimeError.value
    ? `请修复我的智能应用“${detail.name}”（appId=${detail.id}）。当前运行错误：${runtimeError.value}。请先读取应用源码，修改后保存新版本。`
    : `请帮我修改智能应用“${detail.name}”（appId=${detail.id}），先读取应用源码并询问我希望调整什么。`;
  try { void router.push(createApplicationChatRoute(prompt)); }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '无法打开对话'; }
}
</script>

<template>
  <section class="workspace-page dynamic-app-page">
    <p v-if="loading" role="status">正在加载应用…</p>
    <template v-else-if="application">
      <header class="application-header">
        <div class="application-heading">
          <RouterLink to="/apps" class="back-link"><el-icon><ArrowLeft /></el-icon>智能应用</RouterLink>
          <h1>{{ application.name }}</h1>
          <p>{{ application.description }}</p>
        </div>
        <button v-if="panel === 'run'" type="button" aria-label="重新运行应用" @click="runtimeError = ''; revision++"><el-icon><RefreshRight /></el-icon></button>
      </header>
      <p v-if="error" class="page-error" role="alert">{{ error }}</p>
      <div v-if="panel === 'run' && runtimeError" class="runtime-repair">
        <span>应用运行遇到问题</span><button type="button" @click="editWithAgent">让 Agent 修复</button>
      </div>
      <div v-if="panel === 'edit'" class="application-editor">
        <div class="editor-heading"><h2>编辑应用</h2><div>
          <button type="button" :disabled="busy" @click="editWithAgent">用对话修改</button>
          <button type="button" :disabled="busy" @click="returnToApplication">返回应用</button>
        </div></div>
        <div class="metadata-fields">
          <label>应用名称<input v-model="draft.name" maxlength="100" :disabled="busy"></label>
          <label>应用介绍<input v-model="draft.description" maxlength="1000" :disabled="busy"></label>
        </div>
        <fieldset :disabled="busy"><legend>应用能力</legend><label v-for="permission in permissions" :key="permission.value">
          <input v-model="draft.permissions" type="checkbox" :value="permission.value">{{ permission.label }}
        </label></fieldset>
        <div class="editor-tabs" role="tablist" aria-label="应用源码">
          <button v-for="tab in (['source', 'html', 'css'] as const)" :key="tab" type="button" role="tab"
            :aria-selected="editorTab === tab" @click="editorTab = tab">{{ tab === 'source' ? 'TypeScript' : tab.toUpperCase() }}</button>
        </div>
        <textarea v-model="draft[editorTab]" :disabled="busy" :aria-label="`${editorTab} 源码`" spellcheck="false" />
        <footer><span>保存前检查 TypeScript 语法，每次保存保留独立版本。</span>
          <button type="button" class="primary-button" :disabled="busy || !draft.name.trim()" @click="save">{{ busy ? '正在保存…' : '保存并运行' }}</button></footer>
      </div>
      <div v-else-if="panel === 'versions'" class="application-versions">
        <div class="versions-heading"><h2>版本记录</h2><button type="button" :disabled="busy" @click="returnToApplication">返回应用</button></div>
        <p v-if="busy" role="status">正在加载…</p>
        <article v-for="version in versions" :key="version.version" class="version-row">
          <div><strong>{{ version.name }}</strong><p>{{ version.createTime }} · {{ version.version.slice(0, 8) }} · {{ Math.ceil(version.sizeBytes / 1024) }} KB</p></div>
          <span v-if="version.current" class="current-version">当前版本</span>
          <button v-else type="button" :disabled="busy" @click="activate(version)">恢复此版本</button>
        </article>
      </div>
      <ApplicationRuntime v-else-if="application.currentVersion" :key="`${application.id}-${application.currentVersion}-${revision}`"
        :application-id="application.id" :version="application.currentVersion" :content="application.content" @error="runtimeError = $event" />
      <footer class="application-footer"><span>版本 {{ versionLabel }} · 个人应用</span></footer>
    </template>
    <div v-else role="alert" class="page-error">{{ error || '应用不存在或已删除' }}<RouterLink to="/apps">返回应用列表</RouterLink></div>
  </section>
</template>

<style scoped>
.dynamic-app-page { display: flex; flex-direction: column; padding: 0; min-height: calc(100dvh - 64px); }
.application-header { display: flex; justify-content: space-between; align-items: center; gap: 24px; padding: 24px 32px; border-bottom: 1px solid var(--line); }
.back-link { display: inline-flex; align-items: center; gap: 6px; color: var(--text-soft); font-size: 12px; text-decoration: none; }
h1 { margin: 8px 0 4px; font-size: 24px; font-weight: 500; }
.application-heading p { margin: 0; color: var(--text-soft); font-size: 13px; }
button { display: inline-flex; align-items: center; justify-content: center; padding: 8px 12px; border: 1px solid var(--line); border-radius: 7px; background: var(--surface); color: var(--text); font-size: 13px; cursor: pointer; }
button:hover { border-color: var(--line-strong); }
button:disabled { opacity: .5; cursor: default; }
button:focus-visible, input:focus-visible, textarea:focus-visible { outline: 2px solid var(--text-accent); outline-offset: 2px; }
.page-error { margin: 0; padding: 16px 32px; color: var(--danger-text); white-space: pre-wrap; }
.page-error a { margin-left: 16px; }
.runtime-repair { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 12px 32px; color: var(--danger-text); font-size: 13px; border-bottom: 1px solid var(--line); }
.application-editor, .application-versions { padding: 24px 32px; }
.editor-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 20px; }
.editor-heading > div { display: flex; flex-wrap: wrap; gap: 8px; }
.metadata-fields { display: grid; grid-template-columns: 1fr 2fr; gap: 20px; }
.metadata-fields label { display: flex; flex-direction: column; gap: 8px; font-size: 13px; color: var(--text-soft); }
input:not([type="checkbox"]) { padding: 10px 12px; border: 1px solid var(--line); border-radius: 7px; color: var(--text); background: var(--surface); }
fieldset { margin: 20px 0; padding: 12px 16px; border: 1px solid var(--line); border-radius: 8px; font-size: 13px; }
fieldset label { display: inline-flex; align-items: center; gap: 6px; margin-right: 20px; }
legend { color: var(--text-soft); padding: 0 6px; }
.editor-tabs { display: flex; gap: 4px; margin-bottom: 12px; }
.editor-tabs [aria-selected="true"] { background: var(--surface-soft); border-color: var(--line-strong); }
textarea { display: block; width: 100%; min-height: 420px; padding: 16px; resize: vertical; border: 1px solid var(--line); border-radius: 8px; color: var(--text); background: var(--surface); font: 13px/1.7 "SFMono-Regular", Consolas, monospace; tab-size: 2; }
.application-editor footer { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-top: 16px; }
.application-editor footer span { color: var(--text-muted); font-size: 12px; }
.primary-button { background: var(--text-accent); color: var(--surface); border-color: transparent; }
.versions-heading, .version-row { display: flex; align-items: center; justify-content: space-between; gap: 24px; }
h2 { font-size: 18px; font-weight: 500; }
.version-row { padding: 20px 0; border-bottom: 1px solid var(--line); }
.version-row strong { font-weight: 500; }
.version-row p, .current-version { margin: 6px 0 0; color: var(--text-muted); font-size: 12px; }
.application-footer { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 12px 32px; border-top: 1px solid var(--line); color: var(--text-muted); font-size: 12px; }
@media (max-width: 800px) {
  .application-header { flex-direction: column; align-items: stretch; padding: 20px; }
  .application-editor, .application-versions { padding: 20px; }
  .metadata-fields { grid-template-columns: 1fr; }
  .editor-heading { flex-wrap: wrap; }
  .application-footer { padding: 12px 20px; }
}
@media (max-width: 760px) {
  .back-link { margin-left: 48px; }
}
</style>
