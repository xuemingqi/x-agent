<script setup lang="ts">
import { ArrowLeft, Close, Plus } from '@element-plus/icons-vue';
import { computed, onBeforeUnmount, ref } from 'vue';

import { analyzeHttpRequest, getHttpLoadTest, sendHttpRequest, startHttpLoadTest, stopHttpLoadTest } from '@/api/http-lab';
import HttpLoadPanel from '@/components/apps/HttpLoadPanel.vue';
import HttpRequestEditor from '@/components/apps/HttpRequestEditor.vue';
import HttpResponsePanel from '@/components/apps/HttpResponsePanel.vue';
import { useModelSettingStore } from '@/stores/model-setting';
import type { HttpAnalysisRequest, HttpLoadOptions, HttpRequestDraft, HttpRequestSpec } from '@/types/http-lab';

const modelSetting = useModelSettingStore();
const drafts = ref<HttpRequestDraft[]>([newDraft('Echo 示例', 'https://postman-echo.com/get')]);
const selectedId = ref(drafts.value[0]!.id);
const current = computed(() => drafts.value.find(draft => draft.id === selectedId.value)!);
const resultTab = ref<'response' | 'load'>('response');
const analysisTarget = ref<'request' | 'response' | 'load'>('request');
const question = ref('');
const modelId = ref('');
const sendingId = ref('');
const analyzingId = ref('');
const starting = ref(false);
const stopping = ref(false);
const error = ref('');
const pollError = ref('');
const activeRun = ref<{ draftId: string; runId: string }>();
const sendController = ref<AbortController>();
const analysisController = ref<AbortController>();
let pollTimer: ReturnType<typeof setTimeout> | undefined;
let disposed = false;

const responseChanged = computed(() => current.value.responseRequest
  && JSON.stringify(current.value.responseRequest) !== JSON.stringify(current.value.request));

function newDraft(name = '未命名请求', url = ''): HttpRequestDraft {
  return {
    id: crypto.randomUUID(), name,
    request: { method: 'GET', url, query: [], headers: [], body: '', timeoutMs: 10000 },
  };
}

function copyRequest(request: HttpRequestSpec): HttpRequestSpec {
  return { ...request, query: request.query.map(field => ({ ...field })), headers: request.headers.map(field => ({ ...field })) };
}

function addRequest(): void {
  const draft = newDraft();
  drafts.value.push(draft);
  selectedId.value = draft.id;
  analysisTarget.value = 'request';
}

function removeRequest(): void {
  if (activeRun.value?.draftId === current.value.id || sendingId.value === current.value.id || analyzingId.value === current.value.id) return;
  drafts.value = drafts.value.filter(draft => draft.id !== selectedId.value);
  if (!drafts.value.length) drafts.value.push(newDraft());
  selectedId.value = drafts.value[0]!.id;
}

function validRequest(): boolean {
  const request = current.value.request;
  try {
    const url = new URL(request.url);
    if (!['http:', 'https:'].includes(url.protocol)) throw new Error();
    if (!Number.isInteger(request.timeoutMs) || request.timeoutMs < 100 || request.timeoutMs > 30000) throw new Error();
    return true;
  } catch {
    error.value = '请填写完整的 HTTP / HTTPS 地址，超时范围为 100～30,000 ms。';
    return false;
  }
}

async function send(): Promise<void> {
  if (sendingId.value || !validRequest()) return;
  const draft = current.value;
  const snapshot = copyRequest(draft.request);
  sendingId.value = draft.id;
  error.value = '';
  resultTab.value = 'response';
  const controller = new AbortController();
  sendController.value = controller;
  try {
    const response = await sendHttpRequest(snapshot, controller.signal);
    draft.responseRequest = snapshot;
    draft.response = response;
    if (selectedId.value === draft.id) analysisTarget.value = 'response';
  } catch (cause) {
    if (!controller.signal.aborted) error.value = message(cause);
  } finally {
    sendingId.value = '';
  }
}

async function startLoad(options: HttpLoadOptions): Promise<void> {
  if (starting.value || activeRun.value || !validRequest()) return;
  const draft = current.value;
  const snapshot = copyRequest(draft.request);
  starting.value = true;
  error.value = '';
  pollError.value = '';
  try {
    const run = await startHttpLoadTest({ ...options, request: snapshot });
    draft.loadRequest = snapshot;
    draft.loadTest = run;
    if (disposed) {
      if (run.status === 'RUNNING') await stopHttpLoadTest(run.id);
      return;
    }
    if (selectedId.value === draft.id) analysisTarget.value = 'load';
    if (run.status === 'RUNNING') {
      activeRun.value = { draftId: draft.id, runId: run.id };
      schedulePoll();
    }
  } catch (cause) {
    error.value = message(cause);
  } finally {
    starting.value = false;
  }
}

function schedulePoll(): void {
  clearTimeout(pollTimer);
  if (!disposed && activeRun.value && !stopping.value) pollTimer = setTimeout(pollRun, 1000);
}

async function pollRun(): Promise<void> {
  const active = activeRun.value;
  if (!active || disposed || stopping.value) return;
  try {
    const result = await getHttpLoadTest(active.runId);
    if (activeRun.value?.runId !== active.runId || disposed || stopping.value) return;
    const draft = drafts.value.find(item => item.id === active.draftId);
    if (draft) draft.loadTest = result;
    pollError.value = '';
    if (result.status !== 'RUNNING') activeRun.value = undefined;
  } catch (cause) {
    pollError.value = `状态查询失败：${message(cause)}。运行可能仍在继续，可以重试或停止。`;
  } finally {
    schedulePoll();
  }
}

async function stopLoad(): Promise<void> {
  const active = activeRun.value;
  if (!active || stopping.value) return;
  stopping.value = true;
  clearTimeout(pollTimer);
  try {
    const result = await stopHttpLoadTest(active.runId);
    const draft = drafts.value.find(item => item.id === active.draftId);
    if (draft) draft.loadTest = result;
    activeRun.value = undefined;
    pollError.value = '';
  } catch (cause) {
    pollError.value = `停止失败：${message(cause)}。可再次点击停止，任务也会在配置时长结束。`;
  } finally {
    stopping.value = false;
    schedulePoll();
  }
}

async function analyze(): Promise<void> {
  if (analyzingId.value || !modelSetting.requireConfigured()) return;
  const draft = current.value;
  const data: HttpAnalysisRequest = { request: copyRequest(draft.request), question: question.value, modelId: modelId.value || undefined };
  let source = '当前请求配置';
  if (analysisTarget.value === 'response') {
    if (!draft.response || !draft.responseRequest) return;
    data.request = copyRequest(draft.responseRequest);
    data.response = draft.response;
    source = '上次调试结果';
  } else if (analysisTarget.value === 'load') {
    if (!draft.loadTest || !draft.loadRequest) return;
    data.request = copyRequest(draft.loadRequest);
    data.loadTest = draft.loadTest;
    source = '本轮压测快照';
  } else if (!validRequest()) return;
  analyzingId.value = draft.id;
  error.value = '';
  const controller = new AbortController();
  analysisController.value = controller;
  try {
    draft.analysis = await analyzeHttpRequest(data, controller.signal);
    draft.analysisSource = source;
  } catch (cause) {
    if (!controller.signal.aborted) error.value = message(cause);
  } finally {
    analyzingId.value = '';
  }
}

function message(cause: unknown): string {
  return cause instanceof Error ? cause.message : '操作失败，请重试';
}

onBeforeUnmount(() => {
  disposed = true;
  clearTimeout(pollTimer);
  sendController.value?.abort();
  analysisController.value?.abort();
  if (activeRun.value) {
    void stopHttpLoadTest(activeRun.value.runId).catch(() => {
      // 页面已离开时无法展示重试操作，服务端仍会按本轮时长自动停止。
    });
  }
});
</script>

<template>
  <section class="http-lab">
    <header class="lab-header">
      <div><RouterLink to="/apps" class="back-link"><el-icon><ArrowLeft /></el-icon>智能应用</RouterLink><h1>HTTP 请求实验室</h1><p>调试请求，查看响应与性能测量结果</p></div>
      <div class="server-badge"><span /> 从应用服务器发出</div>
    </header>
    <div class="lab-workspace">
      <aside class="request-strip">
        <div class="request-strip-heading"><span>本次请求</span><button type="button" class="icon-button" aria-label="新建请求" data-testid="add-request" @click="addRequest"><el-icon><Plus /></el-icon></button></div>
        <button v-for="draft in drafts" :key="draft.id" type="button" :class="['request-item', { selected: selectedId === draft.id }]"
          @click="selectedId = draft.id; analysisTarget = 'request'">
          <span class="method-label">{{ draft.request.method }}</span><span>{{ draft.name || '未命名请求' }}</span><i v-if="draft.loadTest?.status === 'RUNNING'" />
        </button>
        <p class="request-strip-note">请求与结果仅保留在本次页面中，刷新后清空。</p>
      </aside>
      <main class="request-workspace">
        <div class="request-title">
          <input v-model="current.name" aria-label="请求名称" maxlength="100" placeholder="给这个请求起个名字">
          <button class="text-button" type="button" :disabled="activeRun?.draftId === current.id || sendingId === current.id || analyzingId === current.id" @click="removeRequest">移除</button>
        </div>
        <p v-if="error" class="lab-error" role="alert">{{ error }}<button type="button" class="icon-button" aria-label="关闭错误提示" @click="error = ''"><el-icon><Close /></el-icon></button></p>
        <HttpRequestEditor v-model="current.request" :sending="sendingId === current.id" :disabled="!!sendingId" @send="send" />
        <div class="result-navigation">
          <div class="lab-tabs" role="tablist" aria-label="执行结果">
            <button type="button" role="tab" :aria-selected="resultTab === 'response'" :class="{ active: resultTab === 'response' }" @click="resultTab = 'response'">请求调试</button>
            <button type="button" role="tab" :aria-selected="resultTab === 'load'" :class="{ active: resultTab === 'load' }" @click="resultTab = 'load'" data-testid="load-tab">性能压测 <span v-if="current.loadTest?.status === 'RUNNING'" class="live-dot" /></button>
          </div>
          <button v-if="sendingId === current.id" type="button" class="text-button" @click="sendController?.abort()">取消等待</button>
        </div>
        <div class="result-content">
          <template v-if="resultTab === 'response'">
            <p v-if="responseChanged" class="muted snapshot-note">请求配置已修改，以下结果对应上一次发送时的配置。</p>
            <HttpResponsePanel :response="current.response" :pending="sendingId === current.id" />
          </template>
          <template v-else>
            <p v-if="pollError" class="lab-error" role="alert">{{ pollError }}</p>
            <p v-if="activeRun && activeRun.draftId !== current.id" class="muted">其他请求正在压测，请切换到该请求查看或停止。</p>
            <HttpLoadPanel :result="current.loadTest" :starting="starting" :stopping="stopping" :disabled="!!activeRun || !current.request.url.trim()" @start="startLoad" @stop="stopLoad" @refresh="pollRun" />
          </template>
        </div>
      </main>
      <aside class="analysis-panel">
        <div class="analysis-heading"><h2>AI 请求分析</h2></div>
        <p class="analysis-intro">带着实测结果，找到下一步排查方向。</p>
        <label class="analysis-label" for="analysis-target">分析对象</label>
        <select id="analysis-target" v-model="analysisTarget"><option value="request">当前请求配置</option><option value="response" :disabled="!current.response">上次调试结果</option><option value="load" :disabled="!current.loadTest">本轮压测结果</option></select>
        <label class="analysis-label" for="analysis-model">分析模型</label>
        <select id="analysis-model" v-model="modelId"><option value="">使用我的默认模型</option><option v-for="model in modelSetting.models" :key="model.id" :value="model.id">{{ model.name || model.modelName }}</option></select>
        <textarea v-model="question" aria-label="分析问题" rows="3" maxlength="2000" placeholder="例如：为什么 P95 明显高于平均耗时？" />
        <button class="primary-button analyze-button" type="button" :disabled="!!analyzingId" data-testid="analyze" @click="analyze">{{ analyzingId === current.id ? '正在分析…' : '分析请求' }}</button>
        <div v-if="current.analysis" class="analysis-result">
          <span class="analysis-source">{{ current.analysisSource }}</span>
          <p class="analysis-summary">{{ current.analysis.summary }}</p>
          <template v-for="section in ([['observations', '观察结果'], ['suggestions', '下一步建议'], ['limitations', '分析依据与限制']] as const)" :key="section[0]">
            <template v-if="current.analysis[section[0]].length"><h3>{{ section[1] }}</h3><ul><li v-for="(item, index) in current.analysis[section[0]]" :key="index">{{ item }}</li></ul></template>
          </template>
        </div>
        <div v-else class="analysis-placeholder"><p>可以先检查请求配置，也可以发送请求或完成压测后再分析。</p></div>
      </aside>
    </div>
  </section>
</template>

<style>
.http-lab { --lab-accent: var(--primary); --lab-mono: 'SFMono-Regular', Consolas, 'Liberation Mono', monospace; --lab-code-bg: color-mix(in srgb, var(--surface-soft) 70%, var(--surface)); height: 100%; overflow-y: auto; color: var(--text); padding: var(--workspace-gutter); background: var(--bg); }
.http-lab * { box-sizing: border-box; }
.http-lab .lab-header { display: flex; align-items: center; justify-content: space-between; gap: 24px; margin-bottom: 24px; width: min(var(--workspace-max-width), 100%); margin-inline: auto; }
.http-lab .back-link { display: inline-flex; align-items: center; gap: 8px; color: var(--text-soft); font-size: 12px; text-decoration: none; }
.http-lab h1 { font-size: 28px; font-weight: 500; margin: 12px 0 8px; display: flex; align-items: center; gap: 12px; }
.http-lab .lab-header p { font-size: 13px; color: var(--text-soft); margin: 0; }
.http-lab .server-badge { display: flex; align-items: center; gap: 8px; font-size: 12px; color: var(--text-soft); white-space: nowrap; }
.http-lab .server-badge > span, .http-lab .live-dot, .http-lab .request-item i { display: inline-block; width: 6px; height: 6px; border-radius: 50%; background: var(--lab-accent); }
.http-lab .lab-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 304px;
  grid-template-areas: "requests requests" "request analysis";
  width: min(var(--workspace-max-width), 100%);
  margin: 0 auto;
  border: 1px solid var(--line);
  border-radius: 12px;
  overflow: hidden;
  background: var(--surface);
}
.http-lab button { cursor: pointer; font-family: inherit; transition: background-color 160ms var(--ease), border-color 160ms var(--ease), color 160ms var(--ease); }
.http-lab :is(button, input, select, textarea, a):focus-visible { outline: 2px solid var(--lab-accent); outline-offset: 2px; }
.http-lab button:disabled { opacity: .45; cursor: not-allowed; }
.http-lab input:not([type=checkbox]), .http-lab select, .http-lab textarea { padding: 12px; border: 1px solid var(--line); border-radius: 8px; font-size: 12px; color: var(--text); background: var(--surface); }
.http-lab input:focus, .http-lab textarea:focus, .http-lab select:focus { border-color: var(--lab-accent); box-shadow: 0 0 0 2px color-mix(in srgb, var(--lab-accent) 10%, transparent); }
.http-lab .primary-button, .http-lab .stop-button { border: 0; border-radius: 8px; padding: 12px 16px; background: var(--button-bg); color: var(--button-text); font-size: 12px; font-weight: 500; white-space: nowrap; }
.http-lab .primary-button:hover:not(:disabled) { background: var(--button-hover); }
.http-lab .stop-button { background: var(--danger); color: var(--bg); }
.http-lab .text-button, .http-lab .icon-button { border: 0; background: transparent; color: var(--text-soft); padding: 8px 4px; border-radius: 8px; font-size: 12px; }
.http-lab .text-button:hover, .http-lab .icon-button:hover { color: var(--lab-accent); }
.http-lab .icon-button { font-size: 18px; }
.http-lab .request-strip {
  grid-area: requests;
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  padding: 12px 16px;
  overflow-x: auto;
  border-bottom: 1px solid var(--line);
  background: var(--surface-subtle);
}
.http-lab .request-strip-heading { display: flex; flex-shrink: 0; align-items: center; gap: 12px; padding-right: 8px; font-size: 12px; color: var(--text-soft); }
.http-lab .request-item { display: flex; align-items: center; flex: 0 0 auto; max-width: 240px; text-align: left; gap: 8px; border: 1px solid transparent; background: transparent; padding: 12px 8px; border-radius: 8px; font-size: 12px; color: var(--text-soft); }
.http-lab .request-item > span:nth-child(2) { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.http-lab .request-item.selected { background: var(--surface); border-color: var(--line); color: var(--text); }
.http-lab .method-label { font: 11px var(--lab-mono); font-weight: 500; color: var(--lab-accent); flex-shrink: 0; }
.http-lab .request-strip-note { flex-shrink: 0; margin: 0 0 0 auto; padding-left: 16px; font-size: 12px; line-height: 1.5; color: var(--text-muted); }
.http-lab .request-workspace { grid-area: request; min-width: 0; padding: 24px; }
.http-lab .request-title { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.http-lab .request-title > input { font-size: 14px; font-weight: 500; border-color: transparent; padding-left: 0; width: min(80%, 360px); }
.http-lab .lab-tabs { display: flex; gap: 24px; }
.http-lab .lab-tabs button { padding: 12px 0; background: transparent; border: 0; border-bottom: 2px solid transparent; color: var(--text-soft); font-size: 12px; }
.http-lab .lab-tabs button.active { color: var(--lab-accent); border-bottom-color: var(--lab-accent); font-weight: 500; }
.http-lab .lab-tabs button > span:not(.live-dot) { padding: 2px 4px; border-radius: 4px; margin-left: 4px; background: var(--surface-soft); font-size: 12px; }
.http-lab .result-navigation { display: flex; align-items: center; justify-content: space-between; margin: 24px -24px 0; padding: 0 24px; border-top: 1px solid var(--line); border-bottom: 1px solid var(--line); }
.http-lab .result-content { padding-top: 24px; }
.http-lab .section-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 24px; flex-wrap: wrap; }
.http-lab h2 { font-size: 13px; font-weight: 500; margin: 0; }
.http-lab .eyebrow { font: 11px var(--lab-mono); color: var(--text-soft); margin-left: 8px; font-weight: 400; }
.http-lab .muted { color: var(--text-soft); font-size: 12px; line-height: 1.7; }
.http-lab .lab-error { border: 1px solid var(--danger-border); border-radius: 8px; background: var(--danger-soft); color: var(--danger-text); padding: 12px; font-size: 12px; overflow-wrap: anywhere; display: flex; justify-content: space-between; align-items: center; }
.http-lab .analysis-panel { grid-area: analysis; min-width: 0; border-left: 1px solid var(--line); padding: 24px; background: color-mix(in srgb, var(--surface-soft) 22%, var(--surface)); }
.http-lab .analysis-heading { display: flex; align-items: center; gap: 8px; }
.http-lab .analysis-intro { font-size: 12px; line-height: 1.8; color: var(--text-soft); margin: 8px 0 24px; }
.http-lab .analysis-label { display: block; margin: 16px 0 8px; font-size: 12px; color: var(--text-soft); }
.http-lab .analysis-panel select, .http-lab .analysis-panel textarea, .http-lab .analyze-button { width: 100%; }
.http-lab .analysis-panel textarea { margin: 16px 0 12px; resize: vertical; font-size: 12px; line-height: 1.7; }
.http-lab .analysis-placeholder { color: var(--text-muted); padding-top: 16px; }
.http-lab .analysis-placeholder p { font-size: 12px; line-height: 1.8; }
.http-lab .analysis-result { border-top: 1px solid var(--line); margin-top: 24px; padding-top: 20px; font-size: 12px; line-height: 1.9; overflow-wrap: anywhere; }
.http-lab .analysis-source { font-size: 12px; color: var(--lab-accent); }
.http-lab .analysis-summary { margin: 12px 0 24px; }
.http-lab .analysis-result h3 { font-size: 12px; margin: 16px 0 8px; }
.http-lab .analysis-result ul { padding-left: 16px; color: var(--text-soft); margin: 0; }
.http-lab .analysis-result li { margin-bottom: 8px; }
@container workspace (max-width: 1000px) {
  .http-lab .lab-workspace {
    grid-template-columns: minmax(0, 1fr);
    grid-template-areas: "requests" "request" "analysis";
  }
  .http-lab .analysis-panel { border-top: 1px solid var(--line); border-left: 0; }
  .http-lab .request-strip-note { display: none; }
}
@media (max-width: 760px) {
  .http-lab { padding: 72px 16px 24px; }
  .http-lab .lab-header { align-items: flex-start; }
  .http-lab .server-badge { display: none; }
  .http-lab .request-workspace, .http-lab .analysis-panel { padding: 16px; }
  .http-lab .result-navigation { margin-inline: -16px; padding-inline: 16px; }
  .http-lab .request-strip { padding: 8px 12px; }
}
@media(prefers-reduced-motion: reduce) { .http-lab button { transition: none; } }
</style>
