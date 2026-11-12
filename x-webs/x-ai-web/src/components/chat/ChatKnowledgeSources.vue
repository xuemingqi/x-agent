<script setup lang="ts">
import { Close, FolderOpened } from '@element-plus/icons-vue';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue';

import type { KnowledgeBase } from '@/types/knowledge';

const props = defineProps<{
  knowledgeBases: KnowledgeBase[];
  selectedIds: string[];
  disabled?: boolean;
  loading?: boolean;
  saving?: boolean;
}>();
const emit = defineEmits<{ change: [ids: string[]]; reload: [] }>();
const picker = ref<HTMLElement>();
const trigger = ref<HTMLButtonElement>();
const opened = ref(false);
const panelId = `knowledge-picker-${useId()}`;
const commonBase = computed(() => props.knowledgeBases.find((base) => base.isDefault));
const optionalBases = computed(() => props.knowledgeBases.filter((base) => !base.isDefault));
const sourceCount = computed(() => props.selectedIds.length + (commonBase.value ? 1 : 0));

async function toggle(): Promise<void> {
  if (props.disabled) return;
  opened.value = !opened.value;
  if (opened.value) {
    await nextTick();
    const firstOption = picker.value?.querySelector<HTMLElement>('input:not(:disabled)');
    (firstOption || picker.value?.querySelector<HTMLElement>('a, .picker-reload, .picker-close'))?.focus();
  }
}

function close(restoreFocus = false): void {
  opened.value = false;
  if (restoreFocus) void nextTick(() => trigger.value?.focus());
}

function closeOutside(event: PointerEvent): void {
  if (!picker.value?.contains(event.target as Node)) close();
}

function closeOnFocusLeave(event: FocusEvent): void {
  if (event.relatedTarget && !picker.value?.contains(event.relatedTarget as Node)) close();
}

function changeSelection(baseId: string, event: Event): void {
  const input = event.target as HTMLInputElement;
  const checked = input.checked;
  // 已有关联保存成功后才改变勾选状态；失败时继续展示原来的范围。
  input.checked = props.selectedIds.includes(baseId);
  if (props.disabled) return;
  emit('change', checked ? [...props.selectedIds, baseId] : props.selectedIds.filter((id) => id !== baseId));
}

watch(() => props.disabled, (disabled) => {
  if (disabled && !props.saving) close();
});
onMounted(() => document.addEventListener('pointerdown', closeOutside, true));
onBeforeUnmount(() => document.removeEventListener('pointerdown', closeOutside, true));
</script>

<template>
  <div ref="picker" class="composer-knowledge-picker" @keydown.esc.stop.prevent="close(true)" @focusout="closeOnFocusLeave">
    <button
      ref="trigger" type="button" class="knowledge-trigger" aria-label="关联知识库"
      aria-haspopup="dialog" :aria-expanded="opened" :aria-controls="panelId" :disabled="disabled"
      @click="toggle"
    >
      <el-icon><FolderOpened /></el-icon><span>知识库</span><span v-if="sourceCount" class="knowledge-source-count">{{ sourceCount }}</span>
    </button>
    <section v-if="opened" :id="panelId" class="knowledge-picker-panel" role="dialog" aria-label="关联知识库">
      <header class="knowledge-picker-header">
        <div><strong>对话知识库</strong><span>公共库自动关联，其他知识库可多选</span></div>
        <button type="button" class="picker-close" aria-label="关闭知识库选择" @click="close(true)"><el-icon><Close /></el-icon></button>
      </header>
      <div class="knowledge-picker-list" :aria-busy="loading || saving">
        <p v-if="loading" class="picker-empty" role="status">正在加载知识库…</p>
        <label v-if="commonBase" class="knowledge-picker-row common-source">
          <input type="checkbox" checked disabled :aria-label="`${commonBase.name}，自动关联`" />
          <span><strong>{{ commonBase.name }}</strong><small>所有对话自动检索</small></span>
          <span class="automatic-label">自动关联</span>
        </label>
        <label v-for="base in optionalBases" :key="base.id" class="knowledge-picker-row" :class="{ selected: selectedIds.includes(base.id) }">
          <input
            type="checkbox" :checked="selectedIds.includes(base.id)" :disabled="disabled" :aria-label="base.name"
            @change="changeSelection(base.id, $event)"
          />
          <span><strong>{{ base.name }}</strong><small>{{ base.description || '在当前对话中检索' }}</small></span>
        </label>
        <p v-if="!loading && !optionalBases.length" class="picker-empty">还没有其他知识库，可以先去创建</p>
      </div>
      <footer class="knowledge-picker-footer">
        <RouterLink to="/knowledge" @click="close()">管理知识库</RouterLink>
        <span v-if="saving" role="status">正在保存…</span>
        <button v-else type="button" class="picker-reload" :disabled="loading || disabled" @click="emit('reload')">刷新列表</button>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.composer-knowledge-picker { display: flex; flex: 0 0 auto; }
.knowledge-trigger { display: flex; align-items: center; gap: 6px; min-height: 36px; padding: 0 8px; border: 0; border-radius: 8px; color: var(--text-control); background: transparent; font-size: 13px; cursor: pointer; }
.knowledge-trigger:hover, .knowledge-trigger[aria-expanded='true'] { color: var(--text); background: var(--surface-control-hover); }
.knowledge-trigger .el-icon { font-size: 16px; }
.knowledge-trigger:disabled { cursor: default; opacity: 0.5; }
.knowledge-source-count { color: var(--text-muted); font-size: 12px; font-variant-numeric: tabular-nums; }
.knowledge-picker-panel { position: absolute; z-index: 20; bottom: calc(100% + 12px); left: 0; display: flex; flex-direction: column; width: min(400px, 100%); max-width: calc(100vw - 24px); max-height: min(440px, calc(100dvh - 120px)); border: 1px solid var(--line-strong); border-radius: 12px; background: var(--floating-panel-bg); box-shadow: 0 8px 24px var(--shadow-strong); }
.knowledge-picker-header { display: flex; flex-shrink: 0; align-items: flex-start; justify-content: space-between; gap: 16px; padding: 16px; border-bottom: 1px solid var(--line); }
.knowledge-picker-header > div { display: flex; flex-direction: column; gap: 4px; }
.knowledge-picker-header strong { font-size: 14px; font-weight: 500; }
.knowledge-picker-header span { color: var(--text-muted); font-size: 12px; line-height: 1.6; }
.picker-close { display: grid; width: 24px; height: 24px; flex-shrink: 0; place-items: center; border: 0; border-radius: 4px; color: var(--text-muted); background: transparent; cursor: pointer; }
.picker-close:hover { color: var(--text); background: var(--surface-control-hover); }
.knowledge-picker-list { min-height: 0; padding: 8px; overflow-y: auto; overscroll-behavior: contain; }
.knowledge-picker-row { display: flex; align-items: center; gap: 12px; padding: 12px; border-radius: 8px; cursor: pointer; }
.knowledge-picker-row:hover, .knowledge-picker-row.selected { background: var(--surface-control); }
.knowledge-picker-row input { width: 16px; height: 16px; margin: 0; flex-shrink: 0; accent-color: var(--primary); }
.knowledge-picker-row > span:not(.automatic-label) { display: flex; flex: 1; flex-direction: column; gap: 4px; min-width: 0; }
.knowledge-picker-row strong { color: var(--text); font-size: 13px; font-weight: 500; line-height: 1.6; overflow-wrap: anywhere; }
.knowledge-picker-row small { color: var(--text-muted); font-size: 12px; line-height: 1.6; overflow-wrap: anywhere; }
.common-source { cursor: default; }
.automatic-label { flex-shrink: 0; color: var(--primary); font-size: 12px; }
.picker-empty { margin: 0; padding: 16px 12px; color: var(--text-muted); font-size: 13px; line-height: 1.7; }
.knowledge-picker-footer { display: flex; flex-shrink: 0; align-items: center; justify-content: space-between; gap: 16px; min-height: 48px; padding: 12px 16px; border-top: 1px solid var(--line); font-size: 12px; }
.knowledge-picker-footer a { color: var(--primary); text-decoration: none; }
.knowledge-picker-footer > span { color: var(--text-muted); }
.picker-reload { padding: 0; border: 0; color: var(--text-muted); background: transparent; font-size: 12px; cursor: pointer; }
button:focus-visible, a:focus-visible, input:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
@media (max-width: 760px) {
  .knowledge-picker-panel { max-height: min(400px, 55dvh); }
  .knowledge-trigger { gap: 4px; padding: 0 6px; }
}
</style>
