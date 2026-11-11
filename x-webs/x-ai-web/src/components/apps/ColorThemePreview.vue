<script setup lang="ts">
import { computed, ref } from 'vue';

import type { ColorTheme } from '@/types/color-theme';
import { paletteVariables } from '@/utils/color-theme';

const props = defineProps<{ theme: ColorTheme; compact?: boolean }>();
const variables = computed(() => paletteVariables(props.theme.palette));
const saved = ref(false);
const notesEnabled = ref(true);
</script>

<template>
  <div class="theme-preview" :class="{ compact }" :style="variables" :aria-label="`${theme.name}的组件预览`">
    <div class="preview-nav">
      <span class="preview-logo">
        <i />
        拾光
      </span>
      <span>
        灵感手记
        <span class="nav-dot">·</span>
        周末计划
      </span>
      <span class="avatar">M</span>
    </div>
    <div class="preview-body">
      <span class="preview-eyebrow">A little room for ideas</span>
      <h2>
        把日子，
        <br>
        过成喜欢的颜色。
      </h2>
      <p class="preview-description">
        散步、阅读，或只是发一会儿呆。
        <br>
        把那些微小的美好，慢慢收集起来。
      </p>
      <div class="preview-actions">
        <button class="preview-primary" type="button" :aria-pressed="saved" @click="saved = !saved">
          {{ saved ? '已收藏这份灵感 ✓' : '收藏这份灵感 ↗' }}
        </button>
        <span>留一点时间给自己</span>
      </div>
      <div class="preview-cards">
        <article class="note-card">
          <div class="note-heading">
            <span>今日的小确幸</span>
            <span class="note-tag">随手记</span>
          </div>
          <p>风经过树梢，阳光刚好落在书页上。</p>
          <label class="preview-input-label">
            为今天起个名字
            <input aria-label="预览手记标题" placeholder="一个慢下来的下午…" maxlength="40">
          </label>
        </article>
        <article class="settings-card">
          <div>
            <strong>慢慢来，也很好</strong>
          </div>
          <p>让灵感有处可放。</p>
          <button
            class="preview-toggle-row"
            type="button"
            role="switch"
            :aria-checked="notesEnabled"
            @click="notesEnabled = !notesEnabled"
          >
            <span>记录好心情</span>
            <span class="preview-switch" :class="{ enabled: notesEnabled }">
              <i />
            </span>
          </button>
        </article>
      </div>
      <div class="preview-footer">
        <span>Made of small moments</span>
        <span>01 / 07</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.theme-preview {
  color: var(--color-text);
  background: var(--color-background);
  border: 1px solid var(--color-border);
  border-radius: 14px;
  overflow: hidden;
  transition: background-color 240ms var(--ease), color 240ms var(--ease);
  container-type: inline-size;
}

.preview-nav {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 20px 28px;
  border-bottom: 1px solid var(--color-border);
  font-size: 11px;
  color: var(--color-muted-text);
}

.preview-logo {
  font-size: 15px;
  color: var(--color-text);
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-right: auto;
}

.preview-logo i {
  width: 13px;
  height: 17px;
  border-radius: 8px 2px 8px 2px;
  background: var(--color-primary);
  transform: rotate(20deg);
}

.nav-dot {
  margin: 0 8px;
}

.avatar {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border: 1px solid var(--color-border);
  border-radius: 50%;
}

.preview-body {
  padding: 24px;
}

.preview-eyebrow {
  color: var(--color-muted-text);
  font-size: 11px;
}

h2 {
  font-size: clamp(24px, 4cqw, 36px);
  line-height: 1.4;
  font-weight: 500;
  margin: 20px 0 16px;
  color: var(--color-text);
}

.preview-description {
  color: var(--color-muted-text);
  font-size: 12px;
  line-height: 1.9;
}

.preview-actions {
  display: flex;
  gap: 16px;
  align-items: center;
  margin: 24px 0 28px;
}

.preview-actions > span {
  color: var(--color-muted-text);
  font-size: 11px;
}

button {
  font: inherit;
  cursor: pointer;
}

.preview-primary {
  background: var(--color-primary);
  color: var(--color-on-primary);
  border: none;
  padding: 12px 20px;
  border-radius: 8px;
  font-size: 12px;
}

.preview-cards {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 16px;
}

.preview-cards article {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 20px;
  font-size: 11px;
}

.note-heading {
  display: flex;
  align-items: center;
  gap: 8px;
}

.note-tag {
  margin-left: auto;
  color: var(--color-muted-text);
  border: 1px solid var(--color-border);
  border-radius: 4px;
  padding: 4px 8px;
  font-size: 11px;
}

.preview-cards p {
  color: var(--color-muted-text);
  line-height: 1.8;
  margin: 16px 0;
}

.preview-input-label {
  display: grid;
  gap: 8px;
  font-size: 11px;
}

.preview-input-label input {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  color: var(--color-text);
  border-radius: 8px;
  padding: 12px;
  width: 100%;
  min-width: 0;
  font: inherit;
  outline-offset: 2px;
}

:is(button, input):focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.preview-input-label input::placeholder {
  color: var(--color-muted-text);
  opacity: 1;
}

.settings-card strong {
  vertical-align: middle;
  font-size: 11px;
  font-weight: 500;
}

.preview-toggle-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 12px;
  border: 0;
  border-top: 1px solid var(--color-border);
  padding: 12px 0 0;
  background: transparent;
  color: var(--color-text);
  font-size: 11px;
}

.preview-switch {
  background: var(--color-border);
  width: 32px;
  height: 20px;
  border-radius: 10px;
  padding: 4px;
  flex-shrink: 0;
}

.preview-switch i {
  display: block;
  width: 12px;
  height: 12px;
  background: var(--color-on-primary);
  border-radius: 50%;
}

.preview-switch.enabled {
  background: var(--color-primary);
}

.preview-switch.enabled i {
  margin-left: 12px;
}

.preview-footer {
  border-top: 1px solid var(--color-border);
  margin-top: 24px;
  padding-top: 16px;
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: var(--color-muted-text);
}

.compact .preview-body {
  padding: 24px;
}

.compact .preview-actions > span, .compact .settings-card {
  display: none;
}

.compact .preview-cards {
  grid-template-columns: 1fr;
}

@container (max-width: 440px) {
  .preview-body {
    padding: 24px;
  }
  .preview-nav {
    padding: 16px 20px;
  }
  .preview-nav > span:nth-child(2), .preview-actions > span {
    display: none;
  }
  .preview-cards {
    grid-template-columns: 1fr;
  }
  .settings-card {
    display: none;
  }
}
@media (prefers-reduced-motion: reduce) {
  .theme-preview { transition: none; }
}
</style>
