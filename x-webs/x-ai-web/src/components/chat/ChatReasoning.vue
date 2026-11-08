<script setup lang="ts">
import {
  ArrowRight,
  CircleCheck,
  Loading,
  Warning,
} from '@element-plus/icons-vue';
import { computed, onBeforeUnmount, ref, watch } from 'vue';

import type { DisplayReasoningItem } from '@/stores/chat';
import { renderMarkdown } from '@/utils/markdown';

const props = defineProps<{
  items: DisplayReasoningItem[];
  pendingText?: string;
  processing?: boolean;
  startedAt?: number;
  endedAt?: number;
}>();
const expanded = ref(Boolean(props.processing));
const currentTime = ref(Date.now());
let timer: ReturnType<typeof setInterval> | undefined;

const elapsedTime = computed(() => {
  const startedAt = props.startedAt;
  if (!startedAt) {
    return '0秒';
  }
  const endedAt = props.endedAt || currentTime.value;
  const seconds = Math.max(1, Math.floor((endedAt - startedAt) / 1000));
  if (seconds < 60) {
    return `${seconds}秒`;
  }
  return `${Math.floor(seconds / 60)}分${seconds % 60}秒`;
});

watch(
  () => props.processing,
  (processing) => {
    if (processing) {
      expanded.value = true;
      startTimer();
      return;
    }
    expanded.value = false;
    stopTimer();
  },
  { immediate: true },
);

function startTimer(): void {
  if (timer) {
    return;
  }
  timer = setInterval(() => {
    currentTime.value = Date.now();
  }, 1000);
}

function stopTimer(): void {
  if (!timer) {
    return;
  }
  clearInterval(timer);
  timer = undefined;
  currentTime.value = Date.now();
}

onBeforeUnmount(stopTimer);
</script>

<template>
  <section class="reasoning-panel">
    <button class="reasoning-toggle" type="button" :aria-expanded="expanded" @click="expanded = !expanded">
      <span>{{ processing ? '思考中' : '已思考' }} {{ elapsedTime }}</span>
      <el-icon :class="{ expanded }"><ArrowRight /></el-icon>
    </button>
    <div v-show="expanded" class="reasoning-content">
      <template v-for="item in items" :key="item.id">
        <div
          v-if="item.type === 'text'"
          class="reasoning-text reasoning-markdown-body markdown-body"
        >
          <small v-if="item.source" class="reasoning-source">{{ item.source }}</small>
          <div v-html="renderMarkdown(item.content)" />
        </div>
        <div v-else class="reasoning-tool" :class="item.state">
          <div class="reasoning-tool-heading">
            <el-icon v-if="item.state === 'running'" class="is-loading"><Loading /></el-icon>
            <el-icon v-else-if="item.state === 'success'"><CircleCheck /></el-icon>
            <el-icon v-else><Warning /></el-icon>
            <span>{{ item.toolName }}</span>
            <small v-if="item.source" class="reasoning-source">{{ item.source }}</small>
            <small>{{ item.content }}</small>
          </div>
          <pre v-if="item.result" class="reasoning-tool-result">{{ item.result }}</pre>
        </div>
      </template>
      <div
        v-if="pendingText"
        class="reasoning-text active reasoning-markdown-body markdown-body"
        v-html="renderMarkdown(pendingText)"
      />
    </div>
  </section>
</template>
