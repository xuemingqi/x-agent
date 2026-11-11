<script setup lang="ts">
import type { TokenUsage } from '@/types/chat';

defineProps<{ usage: TokenUsage }>();

function formatTokens(value: number): string {
  return value.toLocaleString('zh-CN');
}
</script>

<template>
  <details class="token-usage">
    <summary>
      <span>{{ formatTokens(usage.totalTokens) }} tokens</span>
      <span>输入 {{ formatTokens(usage.inputTokens) }} · 输出 {{ formatTokens(usage.outputTokens) }}</span>
    </summary>
    <dl>
      <div><dt>模型调用</dt><dd>{{ usage.modelCalls }} 次</dd></div>
      <div><dt>缓存命中</dt><dd>{{ formatTokens(usage.cachedTokens) }}</dd></div>
      <div><dt>缓存写入</dt><dd>{{ formatTokens(usage.cacheCreationTokens) }}</dd></div>
      <div><dt>推理输出</dt><dd>{{ formatTokens(usage.reasoningTokens) }}</dd></div>
      <div><dt>工具结果输入</dt><dd>{{ formatTokens(usage.toolUsePromptTokens) }}</dd></div>
      <div><dt>模型累计耗时</dt><dd>{{ usage.timeSeconds.toFixed(2) }} 秒</dd></div>
    </dl>
    <p>包含本轮主 Agent 与子 Agent 调用。细分项已包含在输入或输出总量中；0 表示未使用或模型未报告。</p>
  </details>
</template>

<style scoped lang="scss">
.token-usage {
  margin-top: 12px;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.6;

  summary {
    display: flex;
    flex-wrap: wrap;
    gap: 4px 12px;
    cursor: pointer;
    width: fit-content;
  }

  summary::before { content: '▸'; }
  &[open] summary::before { content: '▾'; }

  dl {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    max-width: 440px;
    margin: 10px 0;
    gap: 4px 24px;
  }

  dl > div { display: flex; justify-content: space-between; gap: 12px; }
  dd { margin: 0; color: var(--text-soft); font-variant-numeric: tabular-nums; }
  p { margin: 0; max-width: 560px; }
}
</style>
