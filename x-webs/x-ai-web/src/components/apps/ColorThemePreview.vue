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
      <span class="preview-eyebrow">A LITTLE ROOM FOR IDEAS</span>
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
            <span class="note-icon">✳</span>
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
            <span class="note-icon">◷</span>
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
        <span>MADE OF SMALL MOMENTS</span>
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
  border-radius: 18px;
  overflow: hidden;
  transition: background .25s, color .25s;
  container-type: inline-size;
}

.preview-nav {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 18px 28px;
  border-bottom: 1px solid var(--color-border);
  font-size: 11px;
  color: var(--color-muted-text);
}

.preview-logo {
  font-size: 15px;
  color: var(--color-text);
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 7px;
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
  margin: 0 7px;
}

.avatar {
  width: 27px;
  height: 27px;
  display: grid;
  place-items: center;
  border: 1px solid var(--color-border);
  border-radius: 50%;
}

.preview-body {
  padding: 32px;
}

.preview-eyebrow {
  color: var(--color-muted-text);
  font-size: 9px;
  letter-spacing: 2px;
}

h2 {
  font-size: clamp(26px, 5cqw, 42px);
  line-height: 1.4;
  letter-spacing: -1px;
  font-weight: 600;
  margin: 18px 0 14px;
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
  margin: 23px 0 28px;
}

.preview-actions > span {
  color: var(--color-muted-text);
  font-size: 10px;
}

button {
  font: inherit;
  cursor: pointer;
}

.preview-primary {
  background: var(--color-primary);
  color: var(--color-on-primary);
  border: none;
  padding: 12px 18px;
  border-radius: 8px;
  font-size: 12px;
}

.preview-cards {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 14px;
}

.preview-cards article {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  padding: 18px;
  font-size: 11px;
}

.note-heading {
  display: flex;
  align-items: center;
  gap: 7px;
}

.note-icon {
  color: var(--color-text);
  font-size: 20px;
  margin-right: 5px;
}

.note-tag {
  margin-left: auto;
  color: var(--color-muted-text);
  border: 1px solid var(--color-border);
  border-radius: 4px;
  padding: 3px 5px;
  font-size: 9px;
}

.preview-cards p {
  color: var(--color-muted-text);
  line-height: 1.8;
  margin: 14px 0;
}

.preview-input-label {
  display: grid;
  gap: 7px;
  font-size: 10px;
}

.preview-input-label input {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  color: var(--color-text);
  border-radius: 6px;
  padding: 10px;
  width: 100%;
  min-width: 0;
  font: inherit;
  outline-offset: 3px;
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
  gap: 10px;
  border: 0;
  border-top: 1px solid var(--color-border);
  padding: 13px 0 0;
  background: transparent;
  color: var(--color-text);
  font-size: 10px;
}

.preview-switch {
  background: var(--color-border);
  width: 30px;
  height: 18px;
  border-radius: 20px;
  padding: 3px;
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
  margin-top: 26px;
  padding-top: 15px;
  display: flex;
  justify-content: space-between;
  font-size: 8px;
  letter-spacing: 1px;
  color: var(--color-muted-text);
}

.compact .preview-body {
  padding: 22px;
}

.compact .preview-actions > span, .compact .settings-card {
  display: none;
}

.compact .preview-cards {
  grid-template-columns: 1fr;
}

@container (max-width: 440px) {
  .preview-body {
    padding: 22px;
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
</style>
