<script setup lang="ts">
import { ref } from 'vue';

import type { HttpRequestField, HttpRequestSpec } from '@/types/http-lab';

const model = defineModel<HttpRequestSpec>({ required: true });
defineProps<{ sending: boolean; disabled: boolean }>();
defineEmits<{ send: [] }>();
const tab = ref<'query' | 'headers' | 'body'>('query');
const methods = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'];

function addField(target: HttpRequestField[]): void {
  target.push({ name: '', value: '', enabled: true });
}
</script>

<template>
  <section class="request-editor">
    <form class="request-line" @submit.prevent="$emit('send')">
      <select v-model="model.method" aria-label="请求方法">
        <option v-for="method in methods" :key="method">{{ method }}</option>
      </select>
      <input v-model="model.url" aria-label="请求地址" placeholder="https://api.example.com/v1/users" maxlength="4096" required>
      <button class="primary-button" type="submit" :disabled="disabled || !model.url.trim()" data-testid="send">
        {{ sending ? '发送中…' : '发送请求 ↗' }}
      </button>
    </form>
    <div class="editor-toolbar">
      <div class="lab-tabs" role="tablist" aria-label="请求配置">
        <button v-for="item in (['query', 'headers', 'body'] as const)" :key="item" type="button" role="tab"
          :aria-selected="tab === item" :class="{ active: tab === item }" @click="tab = item">
          {{ { query: 'Params', headers: 'Headers', body: 'Body' }[item] }}
          <span v-if="item !== 'body'">{{ model[item].filter(field => field.enabled && field.name).length }}</span>
        </button>
      </div>
      <label class="timeout-field">超时 <input v-model.number="model.timeoutMs" type="number" min="100" max="30000" step="100" aria-label="超时毫秒"> ms</label>
    </div>
    <div v-if="tab !== 'body'" class="field-table">
      <div class="field-labels"><span /><span>KEY</span><span>VALUE</span><span /></div>
      <div v-for="(field, index) in model[tab]" :key="index" class="field-row">
        <input v-model="field.enabled" type="checkbox" :aria-label="`启用第 ${index + 1} 行`">
        <input v-model="field.name" :aria-label="`第 ${index + 1} 行名称`" placeholder="参数名称" maxlength="256">
        <input v-model="field.value" :aria-label="`第 ${index + 1} 行值`" placeholder="参数值" maxlength="8192">
        <button type="button" class="icon-button" :aria-label="`删除第 ${index + 1} 行`" @click="model[tab].splice(index, 1)">×</button>
      </div>
      <button type="button" class="text-button" :disabled="model[tab].length >= 80" @click="addField(model[tab])">＋ 添加{{ tab === 'query' ? '参数' : '请求头' }}</button>
    </div>
    <div v-else class="body-editor">
      <p class="muted">原始文本 / JSON · Content-Type 请在 Headers 中设置。GET 和 HEAD 不发送正文。</p>
      <textarea v-model="model.body" aria-label="请求正文" spellcheck="false" rows="8" maxlength="131072" placeholder='{"message": "Hello, HTTP Lab"}' />
    </div>
  </section>
</template>

<style scoped>
.request-line { display: flex; gap: 8px; }
.request-line select { width: 108px; color: var(--lab-accent); font-weight: 700; }
.request-line > input { flex: 1; min-width: 80px; font-family: var(--lab-mono); }
.editor-toolbar { display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid var(--line); margin-top: 22px; gap: 12px; flex-wrap: wrap; }
.timeout-field { font-size: 12px; color: var(--text-soft); display: flex; align-items: center; gap: 6px; }
.timeout-field input { width: 82px; padding: 5px 8px; }
.field-labels, .field-row { display: grid; grid-template-columns: 24px minmax(80px, 1fr) minmax(100px, 1.6fr) 28px; align-items: center; gap: 10px; }
.field-labels { padding: 14px 0 10px; font-size: 10px; color: var(--text-soft); letter-spacing: 1.2px; }
.field-row { margin-bottom: 8px; }
.field-row input[type=checkbox] { width: 15px; accent-color: var(--lab-accent); }
.field-row input:not([type=checkbox]) { min-width: 0; width: 100%; font-family: var(--lab-mono); }
.field-table { min-height: 132px; }
.body-editor textarea { width: 100%; resize: vertical; font-family: var(--lab-mono); line-height: 1.6; }
.body-editor p { margin: 12px 0; font-size: 12px; }
@media (max-width: 650px) { .request-line { flex-wrap: wrap; } .request-line > button { width: 100%; } }
</style>
