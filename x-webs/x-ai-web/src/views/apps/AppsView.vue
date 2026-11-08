<script setup lang="ts">
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
  <section class="apps-page">
    <span class="apps-eyebrow">AGENT APPS</span>
    <h1>智能应用</h1>
    <p class="apps-intro">一点灵感，一次动手。打开一个应用，让 AI 成为你手边的小工具。</p>
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
              <span class="app-arrow" aria-hidden="true">↗</span>
            </RouterLink>
            <template v-else>{{ application.name }}</template>
          </h2>
          <p>{{ application.description }}</p>
          <span v-if="!hasApplicationPage(application.id)" class="unavailable">当前前端版本尚未提供此应用页面</span>
        </div>
      </article>
    </div>
    <p v-else>当前版本暂无可用应用。</p>
  </section>
</template>

<style scoped>
.apps-page {
  padding: 42px;
  overflow-y: auto;
  height: 100%;
}

.apps-eyebrow {
  color: var(--text-accent);
  font-size: 11px;
  letter-spacing: 3px;
  font-weight: 700;
}

h1 {
  font-size: 32px;
  margin: 12px 0;
  letter-spacing: -1px;
}

.apps-intro {
  color: var(--text-soft);
  margin-bottom: 36px;
  line-height: 1.8;
}

.apps-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 380px));
  gap: 24px;
}

.app-card {
  position: relative;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 20px;
  overflow: hidden;
  transition: transform 180ms ease, border-color 180ms ease, box-shadow 180ms ease;
}

.app-card:has(.app-link:hover),
.app-card:focus-within {
  border-color: var(--line-strong);
  box-shadow: 0 10px 28px var(--shadow);
  transform: translateY(-3px);
}

.app-art {
  height: 184px;
  background: var(--surface-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
}

.color-art {
  background: #e8eee4;
}

.http-art { background: #e9eee9; }
.http-illustration { width: 230px; padding: 22px; border: 1px solid #c4d2c5; border-radius: 12px; background: #f8faf7; color: #387361; font-family: monospace; }
.http-illustration > span { font-size: 12px; }
.http-illustration > strong { float: right; font-size: 15px; }
.http-illustration svg { display: block; width: 100%; margin-top: 18px; }
.http-illustration path { stroke: #387361; stroke-width: 2; fill: none; }

.color-art span {
  height: 106px;
  width: 42px;
  border-radius: 32px;
  transform: rotate(17deg);
  box-shadow: 0 8px 16px #243d3012;
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
  font-size: 19px;
  margin: 0 0 12px;
}

.app-info p {
  color: var(--text-soft);
  font-size: 13px;
  line-height: 1.8;
  min-height: 47px;
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
  border-radius: 20px;
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
  transition: color 180ms ease, transform 180ms ease;
}

.app-link:hover .app-arrow,
.app-link:focus-visible .app-arrow {
  color: var(--text-accent);
  transform: translate(2px, -2px);
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
  color: var(--text-accent);
}

@media (prefers-reduced-motion: reduce) {
  .app-card,
  .app-arrow {
    transition: none;
    transform: none !important;
  }
}

@media (max-width: 760px) {
  .apps-page {
    padding: 68px 20px 28px;
  }
}
</style>
