<script setup lang="ts">
import { TopRight } from '@element-plus/icons-vue';
import { onMounted, ref } from 'vue';

import { listAgentApps } from '@/api/agent-app';
import { hasApplicationPage } from '@/applications/pages';
import type { AgentApp } from '@/types/agent-app';

const applications = ref<AgentApp[]>([]);
const loading = ref(true);
const error = ref('');

async function loadApplications(): Promise<void> {
  loading.value = true;
  error.value = '';
  try {
    applications.value = await listAgentApps();
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '应用列表加载失败';
  } finally {
    loading.value = false;
  }
}

onMounted(loadApplications);
</script>

<template>
  <section class="workspace-page apps-page">
    <header class="page-header">
      <div>
        <p>Agent apps</p>
        <h1>智能应用</h1>
        <span>打开应用，使用面向具体任务的工具</span>
      </div>
    </header>
    <p v-if="loading" role="status">正在加载应用…</p>
    <div v-else-if="error" role="alert" class="apps-error">
      {{ error }}
      <button type="button" @click="loadApplications">重新加载</button>
    </div>
    <div v-else-if="applications.length" class="apps-grid">
      <article v-for="application in applications" :key="application.id" class="app-card">
        <div class="app-art" :class="{ 'color-art': application.id === 'color-lab', 'http-art': application.id === 'http-lab' }" aria-hidden="true">
          <template v-if="application.id === 'color-lab'">
            <span
              v-for="color in ['#243D30', '#48715C', '#A5BFA0', '#DBE4C6', '#F0DDA9']"
              :key="color"
              :style="{ background: color }"
            />
          </template>
          <div v-else-if="application.id === 'http-lab'" class="http-illustration"><span>GET</span><strong>200 OK</strong><svg viewBox="0 0 180 40"><path d="M0 30H30L42 10L55 33L67 20H95L110 5L125 25H180" /></svg></div>
          <span v-else class="app-initial">{{ application.name.slice(0, 1) }}</span>
        </div>
        <div class="app-info">
          <h2>
            <RouterLink v-if="hasApplicationPage(application.id)" :to="`/apps/${application.id}`" class="app-link">
              {{ application.name }}
              <el-icon class="app-arrow" aria-hidden="true"><TopRight /></el-icon>
            </RouterLink>
            <template v-else>{{ application.name }}</template>
          </h2>
          <p>{{ application.description }}</p>
          <span v-if="!hasApplicationPage(application.id)" class="unavailable">当前前端版本尚未提供此应用页面</span>
        </div>
      </article>
    </div>
    <p v-else>当前版本暂无可用应用</p>
  </section>
</template>

<style scoped>
.apps-page {
  min-width: 0;
}

.apps-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(320px, 100%), 1fr));
  gap: 24px;
  width: min(var(--workspace-max-width), 100%);
  margin: 0 auto;
}

.app-card {
  position: relative;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 12px;
  overflow: hidden;
  transition: border-color 160ms var(--ease);
}

.app-card:has(.app-link:hover),
.app-card:focus-within {
  border-color: var(--line-strong);
}

.app-art {
  height: 200px;
  background: var(--surface-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.http-illustration {
  width: 232px;
  max-width: calc(100% - 32px);
  padding: 20px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface);
  color: var(--primary);
  font-family: "SFMono-Regular", Consolas, monospace;
  font-variant-numeric: tabular-nums;
}
.http-illustration > span { font-size: 12px; }
.http-illustration > strong { float: right; font-size: 14px; font-weight: 500; }
.http-illustration svg { display: block; width: 100%; margin-top: 16px; }
.http-illustration path { stroke: currentColor; stroke-width: 1.5; fill: none; }

.color-art span {
  height: 104px;
  width: 40px;
  border-radius: 8px;
  transform: rotate(17deg);
}

.color-art span:nth-child(even) {
  transform: translateY(12px) rotate(17deg);
}

.app-initial {
  font-size: 48px;
  color: var(--text-accent);
}

.app-info {
  padding: 24px;
}

h2 {
  font-size: 20px;
  font-weight: 500;
  margin: 0 0 12px;
}

.app-info p {
  color: var(--text-soft);
  font-size: 13px;
  line-height: 1.8;
  min-height: 48px;
  margin: 0;
}

.app-link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  color: inherit;
  text-decoration: none;
}

.app-link::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 12px;
}

.app-link:focus-visible {
  outline: none;
}

.app-link:focus-visible::after {
  outline: 2px solid var(--text-accent);
  outline-offset: -4px;
}

.app-arrow {
  flex-shrink: 0;
  color: var(--text-muted);
  font-size: 20px;
  font-weight: 400;
  line-height: 1;
  transition: color 160ms var(--ease);
}

.app-link:hover .app-arrow,
.app-link:focus-visible .app-arrow {
  color: var(--text-accent);
}

.unavailable {
  display: block;
  margin-top: 16px;
  color: var(--text-muted);
  font-size: 12px;
}

.apps-error {
  color: var(--danger-text);
}

.apps-error button {
  margin-left: 12px;
  padding: 8px 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
  color: var(--text-accent);
  background: var(--surface);
  cursor: pointer;
}

.apps-error button:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 2px;
}

@media (prefers-reduced-motion: reduce) {
  .app-card,
  .app-arrow {
    transition: none;
  }
}

</style>
