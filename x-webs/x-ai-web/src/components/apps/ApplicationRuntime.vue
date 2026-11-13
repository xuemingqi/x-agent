<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';

import { executeAgentAppOperation } from '@/api/agent-app';
import type { AgentAppContent } from '@/types/agent-app';
import { compileApplicationSource } from '@/utils/app-compiler';
import { buildApplicationDocument, createApplicationBridge, createApplicationToken } from '@/utils/app-runtime';

const props = defineProps<{ applicationId: string; version: string; content: AgentAppContent }>();
const emit = defineEmits<{ error: [message: string] }>();
const frame = ref<HTMLIFrameElement>();
const documentHtml = ref('');
const error = ref('');
const ready = ref(false);
let disposeBridge: (() => void) | undefined;
let readyTimer: ReturnType<typeof setTimeout> | undefined;
let frameLoads = 0;

function reportError(message: string): void {
  error.value = message;
  emit('error', message);
}

function stop(): void {
  disposeBridge?.();
  disposeBridge = undefined;
  clearTimeout(readyTimer);
}

function frameLoaded(): void {
  if (++frameLoads > 1) {
    stop();
    documentHtml.value = '';
    ready.value = false;
    reportError('应用发生了页面跳转，已停止本次运行');
  }
}

watch(() => [props.applicationId, props.version, props.content] as const, async (_, __, onCleanup) => {
  let disposed = false;
  onCleanup(() => { disposed = true; stop(); });
  documentHtml.value = '';
  error.value = '';
  ready.value = false;
  frameLoads = 0;
  const { applicationId, version, content } = props;
  try {
    const javascript = await compileApplicationSource(content);
    if (disposed) return;
    const channel = createApplicationToken();
    disposeBridge = createApplicationBridge({
      frame: () => frame.value?.contentWindow ?? null, channel, permissions: content.permissions,
      execute: (operation, payload, signal) => executeAgentAppOperation(applicationId, version, operation, payload, signal),
      onError: reportError,
      onReady: () => { ready.value = true; clearTimeout(readyTimer); },
    });
    documentHtml.value = buildApplicationDocument(content, javascript, channel);
    readyTimer = setTimeout(() => {
      if (!ready.value) reportError('应用初始化超时，请重新运行或让 Agent 修复');
    }, 10000);
  } catch (cause) {
    if (!disposed) reportError(cause instanceof Error ? cause.message : '应用加载失败');
  }
}, { immediate: true });

onBeforeUnmount(stop);
</script>

<template>
  <div class="application-runtime">
    <p v-if="error" class="runtime-error" role="alert">{{ error }}</p>
    <p v-if="!ready && !error" class="runtime-loading" role="status">正在准备应用…</p>
    <!-- 允许 submit 事件；实际表单网络提交由应用文档的 CSP form-action 阻断。 -->
    <iframe v-if="documentHtml" ref="frame" :srcdoc="documentHtml" title="智能应用运行页面"
      sandbox="allow-scripts allow-forms" referrerpolicy="no-referrer"
      allow="camera 'none'; microphone 'none'; geolocation 'none'; display-capture 'none'; clipboard-read 'none'; clipboard-write 'none'"
      @load="frameLoaded" />
  </div>
</template>

<style scoped>
.application-runtime { position: relative; flex: 1; min-width: 0; min-height: 560px; }
iframe { display: block; width: 100%; min-height: 560px; height: 100%; border: 0; background: #fff; }
.runtime-error { margin: 0; padding: 16px 20px; color: var(--danger-text); white-space: pre-wrap; border-bottom: 1px solid var(--line); }
.runtime-loading { margin: 0; padding: 20px; color: var(--text-soft); }
</style>
