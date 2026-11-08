<script setup lang="ts">
import { computed, ref } from 'vue';
import type { HttpExecution } from '@/types/http-lab';

const props = defineProps<{ response?: HttpExecution; pending: boolean }>();
const tab = ref<'body' | 'headers'>('body');
const formattedBody = computed(() => {
  const body = props.response?.body || '';
  try { return JSON.stringify(JSON.parse(body), null, 2); } catch { return body; }
});
</script>

<template>
  <section class="response-panel" :aria-busy="pending">
    <div class="section-heading">
      <h2>响应 <span class="eyebrow">RESPONSE</span></h2>
      <div v-if="response" class="response-metrics">
        <span :class="['status-tag', { failed: response.statusCode < 200 || response.statusCode >= 300 || response.error }]">{{ response.statusCode || 'NETWORK ERROR' }}</span>
        <span>{{ response.elapsedMs.toFixed(1) }} ms</span>
        <span>{{ (response.responseBytes / 1024).toFixed(1) }} KB</span>
      </div>
    </div>
    <div v-if="!response" class="empty-response">
      <span class="empty-glyph">↗</span>
      <strong>{{ pending ? '正在等待接口响应…' : '让请求跑起来' }}</strong>
      <p>发送请求后，在这里查看响应正文、状态码和耗时。</p>
    </div>
    <template v-else>
      <p v-if="response.error" class="lab-error" role="alert">{{ response.error }}</p>
      <p v-if="response.truncated" class="muted">响应超过 256 KB，仅展示已读取的前缀；本次耗时不代表完整响应耗时。</p>
      <div class="lab-tabs" role="tablist" aria-label="响应内容">
        <button type="button" role="tab" :aria-selected="tab === 'body'" :class="{ active: tab === 'body' }" @click="tab = 'body'">Body</button>
        <button type="button" role="tab" :aria-selected="tab === 'headers'" :class="{ active: tab === 'headers' }" @click="tab = 'headers'">Headers</button>
      </div>
      <pre v-if="tab === 'body'" class="response-code">{{ formattedBody || '（空响应体）' }}</pre>
      <dl v-else class="response-headers">
        <template v-for="(values, name) in response.headers" :key="name"><dt>{{ name }}</dt><dd>{{ values.join('\n') }}</dd></template>
      </dl>
    </template>
  </section>
</template>

<style scoped>
.response-metrics { display: flex; align-items: center; gap: 14px; font-family: var(--lab-mono); font-size: 12px; }
.status-tag { background: color-mix(in srgb, var(--lab-accent) 12%, transparent); color: var(--lab-accent); padding: 4px 8px; border-radius: 5px; }
.status-tag.failed { color: #d96956; background: #d9695612; }
.empty-response { min-height: 235px; display: flex; flex-direction: column; justify-content: center; align-items: center; color: var(--text-soft); }
.empty-response strong { font-weight: 500; color: var(--text); }
.empty-response p { font-size: 12px; text-align: center; }
.empty-glyph { font-size: 28px; margin-bottom: 16px; color: var(--lab-accent); }
.response-code { margin: 0; padding: 18px; min-height: 200px; max-height: 440px; overflow: auto; background: var(--lab-code-bg); font-family: var(--lab-mono); font-size: 12px; line-height: 1.7; white-space: pre-wrap; overflow-wrap: anywhere; border-radius: 8px; }
.response-headers { display: grid; grid-template-columns: minmax(100px, 1fr) 2fr; font: 12px/1.8 var(--lab-mono); gap: 8px 18px; max-height: 380px; overflow: auto; }
.response-headers dt { color: var(--text-soft); overflow-wrap: anywhere; }
.response-headers dd { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
