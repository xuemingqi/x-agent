<script setup lang="ts">
import { computed, reactive } from 'vue';
import type { HttpLoadOptions, HttpLoadResult } from '@/types/http-lab';

const props = defineProps<{ result?: HttpLoadResult; starting: boolean; stopping: boolean; disabled: boolean }>();
defineEmits<{ start: [options: HttpLoadOptions]; stop: []; refresh: [] }>();
const options = reactive<HttpLoadOptions>({ concurrency: 5, durationSeconds: 30, requestsPerSecond: 10 });
const running = computed(() => props.result?.status === 'RUNNING');
const statusNames = { RUNNING: '正在运行', COMPLETED: '已完成', STOPPED: '已停止', FAILED: '执行失败' };
const metrics = computed(() => props.result ? [
  ['已完成请求', props.result.completed.toLocaleString(), '次'],
  ['实际吞吐', props.result.throughput.toFixed(1), 'req/s'],
  ['失败率', props.result.errorRate.toFixed(1), '%'],
  ['平均耗时', props.result.averageMs.toFixed(1), 'ms'],
  ['P95', props.result.p95Ms.toLocaleString(), 'ms'],
  ['P99', props.result.p99Ms.toLocaleString(), 'ms'],
] : []);
const chart = computed(() => {
  const result = props.result;
  if (!result) return [];
  const seconds = Math.max(1, Math.ceil(result.elapsedSeconds));
  return Array.from({ length: seconds + 1 }, (_, second) => result.timeline.find(point => point.second === second)
    || { second, completed: 0, failed: 0, averageMs: 0 });
});
const maxLatency = computed(() => Math.max(1, ...chart.value.map(point => point.averageMs)));
const maxRate = computed(() => Math.max(1, ...chart.value.map(point => point.completed)));
const latencyPoints = computed(() => chart.value.map((point, index) =>
  `${index * 600 / Math.max(1, chart.value.length - 1)},${115 - point.averageMs / maxLatency.value * 100}`).join(' '));
</script>

<template>
  <section class="load-panel">
    <div class="section-heading">
      <h2>接口压测 <span class="eyebrow">Load test</span></h2>
      <span v-if="result" :class="['run-status', { running }]">{{ statusNames[result.status] }} · {{ result.elapsedSeconds.toFixed(1) }} s</span>
    </div>
    <form class="load-options" @submit.prevent="$emit('start', { ...options })">
      <label>最大并发<input v-model.number="options.concurrency" type="number" min="1" max="20" required :disabled="running || starting" aria-label="最大并发"></label>
      <label>持续时间（秒）<input v-model.number="options.durationSeconds" type="number" min="1" max="120" required :disabled="running || starting" aria-label="持续秒数"></label>
      <label>速率上限（req/s）<input v-model.number="options.requestsPerSecond" type="number" min="1" max="100" required :disabled="running || starting" aria-label="每秒速率上限"></label>
      <button v-if="running" type="button" class="stop-button" :disabled="stopping" data-testid="stop-load" @click="$emit('stop')">{{ stopping ? '停止中…' : '■ 停止压测' }}</button>
      <button v-else type="submit" class="primary-button" :disabled="disabled || starting" data-testid="start-load">{{ starting ? '启动中…' : '▷ 开始压测' }}</button>
    </form>
    <p class="muted load-hint">由应用服务器发出请求 · 最多 20 并发 / 120 秒 / 100 req/s · 单轮最多 10,000 次</p>
    <template v-if="result">
      <p class="run-target"><strong>{{ result.method }}</strong> {{ result.url }}</p>
      <div class="metrics-grid">
        <article v-for="[label, value, unit] in metrics" :key="label"><span>{{ label }}</span><strong>{{ value }}<small>{{ unit }}</small></strong></article>
      </div>
      <div v-if="result.completed" class="charts">
        <figure>
          <figcaption>每秒平均耗时 <span>{{ maxLatency.toFixed(0) }} ms</span></figcaption>
          <svg viewBox="0 0 600 130" role="img" aria-label="每秒平均响应耗时趋势">
            <path d="M0 15H600 M0 65H600 M0 115H600" class="chart-grid" />
            <polyline :points="latencyPoints" class="latency-line" />
          </svg>
          <div class="chart-axis"><span>0 s</span><span>{{ result.elapsedSeconds.toFixed(0) }} s</span></div>
        </figure>
        <figure>
          <figcaption>每秒完成请求 <span>{{ maxRate }} req/s</span></figcaption>
          <svg viewBox="0 0 600 130" role="img" aria-label="每秒完成请求数趋势">
            <path d="M0 15H600 M0 65H600 M0 115H600" class="chart-grid" />
            <rect v-for="(point, index) in chart" :key="point.second" :x="index * 600 / chart.length"
              :y="115 - point.completed / maxRate * 100" :width="Math.max(1, 600 / chart.length - 2)"
              :height="point.completed / maxRate * 100" class="throughput-bar" />
          </svg>
          <div class="chart-axis"><span>0 s</span><span>{{ result.elapsedSeconds.toFixed(0) }} s</span></div>
        </figure>
      </div>
      <div class="status-distribution"><span v-for="(count, status) in result.statusCounts" :key="status">{{ status }} <strong>{{ count }}</strong></span></div>
      <p class="muted">P50 {{ result.p50Ms }} ms · 已发出 {{ result.started }} 次 · 取消 {{ result.cancelled }} 次。成功仅表示 HTTP 2xx，取消不计入耗时和失败率。</p>
      <p v-if="result.message" class="muted">{{ result.message }}</p>
      <button v-if="running" class="text-button" type="button" @click="$emit('refresh')">刷新运行状态</button>
    </template>
    <div v-else class="load-empty"><span>▥</span><strong>从一次请求，到一轮测量</strong><p>观察吞吐、延迟与错误分布，再让 AI 解读结果。</p></div>
  </section>
</template>

<style scoped>
.load-options { display: grid; grid-template-columns: repeat(3, minmax(70px, 1fr)) auto; align-items: end; gap: 12px; }
.load-options label { display: flex; flex-direction: column; gap: 8px; font-size: 12px; color: var(--text-soft); }
.load-options input { width: 100%; }
.load-hint { font-size: 11px; margin: 16px 0 24px; }
.run-status { font-size: 12px; color: var(--text-soft); }
.run-status.running { color: var(--lab-accent); }
.run-target { font-family: var(--lab-mono); font-size: 12px; overflow-wrap: anywhere; color: var(--text-soft); }
.run-target strong { color: var(--lab-accent); }
.metrics-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin: 16px 0; }
.metrics-grid article { padding: 16px; border: 1px solid var(--line); border-radius: 8px; }
.metrics-grid article > span { font-size: 11px; color: var(--text-soft); }
.metrics-grid strong { display: block; font: 24px var(--lab-mono); margin-top: 12px; }
.metrics-grid small { font: 11px var(--lab-mono); margin-left: 4px; color: var(--text-soft); }
.charts { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }
.charts figure { margin: 4px 0 16px; min-width: 0; }
.charts figcaption { display: flex; justify-content: space-between; font-size: 11px; color: var(--text-soft); }
.charts svg { width: 100%; margin-top: 16px; }
.chart-grid { stroke: var(--line); stroke-width: 1; fill: none; }
.latency-line { stroke: var(--lab-accent); stroke-width: 3; fill: none; }
.throughput-bar { fill: #779bdf; }
.chart-axis { display: flex; justify-content: space-between; font: 11px var(--lab-mono); color: var(--text-soft); }
.status-distribution { display: flex; gap: 12px; flex-wrap: wrap; }
.status-distribution span { background: var(--surface-soft); padding: 8px 12px; font: 11px var(--lab-mono); border-radius: 8px; }
.status-distribution strong { margin-left: 8px; }
.load-empty { padding: 36px 12px; display: flex; align-items: center; flex-direction: column; color: var(--text-soft); }
.load-empty > span { font-size: 28px; margin-bottom: 16px; color: var(--lab-accent); }
.load-empty strong { font-size: 14px; font-weight: 500; color: var(--text); }
.load-empty p { font-size: 12px; }
@media(max-width: 650px) { .load-options { grid-template-columns: repeat(3, 1fr); } .load-options button { grid-column: 1 / -1; } .metrics-grid { grid-template-columns: repeat(2, 1fr); } .charts { grid-template-columns: 1fr; } }
</style>
