<script setup lang="ts">
import { ArrowLeft, Check, Close, Download, Warning } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { computed, onBeforeUnmount, ref } from 'vue';

import { generateColorTheme } from '@/api/color-theme';
import ColorThemePreview from '@/components/apps/ColorThemePreview.vue';
import { useModelSettingStore } from '@/stores/model-setting';
import { COLOR_ROLES, type ColorRole, type ColorTheme, type ColorThemeGenerateRequest } from '@/types/color-theme';
import { copyTheme, exportThemeCss, INITIAL_COLOR_THEME, isHexColor, paletteContrast } from '@/utils/color-theme';

const modelSetting = useModelSettingStore();
const controlTab = ref<'generate' | 'palette'>('generate');
const prompt = ref('雨后的森林，安静、清新，带一点温暖');
const modelId = ref('');
const dark = ref(false);
const vivid = ref(false);
const current = ref(copyTheme(INITIAL_COLOR_THEME));
const baseline = ref<ColorTheme>();
const history = ref<ColorTheme[]>([]);
const locked = ref<ColorRole[]>([]);
const pending = ref(false);
const error = ref('');
const source = ref('初始示例');
let controller: AbortController | undefined;

const contrast = computed(() => paletteContrast(current.value.palette));
const passingCount = computed(() => contrast.value.filter((item) => item.passes).length);
const allLocked = computed(() => locked.value.length === COLOR_ROLES.length);
const css = computed(() => exportThemeCss(current.value.palette));
const presets = ['雨后的森林，安静、清新', '落日海岸，温暖、松弛', '午夜城市，霓虹、鲜明'];

onBeforeUnmount(() => controller?.abort());

async function generate(improveReadability: boolean): Promise<void> {
  if (pending.value || allLocked.value || !prompt.value.trim() || !modelSetting.requireConfigured()) return;
  const request: ColorThemeGenerateRequest = {
    prompt: prompt.value.trim(), modelId: modelId.value || undefined, dark: dark.value, vivid: vivid.value,
    improveReadability, currentPalette: { ...current.value.palette },
    lockedColors: Object.fromEntries(locked.value.map((key) => [key, current.value.palette[key]])),
  };
  const activeController = new AbortController();
  controller = activeController;
  pending.value = true;
  error.value = '';
  try {
    const result = await generateColorTheme(request, activeController.signal);
    if (activeController.signal.aborted) return;
    current.value = copyTheme(result);
    history.value = [copyTheme(result), ...history.value].slice(0, 6);
    source.value = improveReadability ? 'AI 优化' : 'AI 生成';
  } catch (cause) {
    if (!activeController.signal.aborted) error.value = cause instanceof Error ? cause.message : '生成失败，请重试';
  } finally {
    pending.value = false;
  }
}

function updateColor(role: ColorRole, event: Event): void {
  const input = event.target as HTMLInputElement;
  const value = input.value.trim();
  if (!isHexColor(value)) {
    if (event.type === 'change') {
      ElMessage.warning('请输入完整的六位色值，例如 #346650');
      input.value = current.value.palette[role];
    }
    return;
  }
  current.value.palette[role] = value.toUpperCase();
  source.value = '手动调整';
}

function toggleLock(role: ColorRole): void {
  locked.value = locked.value.includes(role) ? locked.value.filter((key) => key !== role) : [...locked.value, role];
}

function restoreTheme(theme: ColorTheme): void {
  current.value = copyTheme(theme);
  locked.value = [];
  source.value = '历史方案';
  error.value = '';
  ElMessage.success('已切换方案，并清除颜色锁定');
}

async function copyCss(): Promise<void> {
  try {
    await navigator.clipboard.writeText(css.value);
    ElMessage.success('CSS 变量已复制');
  } catch {
    ElMessage.warning('浏览器不允许复制，请使用下载 CSS');
  }
}

function downloadCss(): void {
  const url = URL.createObjectURL(new Blob([css.value], { type: 'text/css;charset=utf-8' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = 'color-theme.css';
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
</script>

<template>
  <section class="color-lab">
    <header class="lab-header">
      <div>
        <RouterLink to="/apps" class="back-link"><el-icon><ArrowLeft /></el-icon>智能应用</RouterLink>
        <h1>
          AI 配色实验室
        </h1>
        <p>描述配色方向，调整色板并检查界面中的文字对比度</p>
      </div>
      <div class="export-actions">
        <button type="button" @click="copyCss">复制 CSS</button>
        <button type="button" @click="downloadCss"><el-icon><Download /></el-icon>下载 CSS</button>
      </div>
    </header>
    <div class="lab-workbench">
      <aside class="lab-controls">
        <div class="control-tabs" role="tablist" aria-label="配色调整方式">
          <button id="color-generation-tab" type="button" role="tab" aria-controls="color-generation-panel"
            :aria-selected="controlTab === 'generate'" @click="controlTab = 'generate'">生成配色</button>
          <button id="color-palette-tab" type="button" role="tab" aria-controls="color-palette-panel"
            :aria-selected="controlTab === 'palette'" @click="controlTab = 'palette'">微调色板</button>
        </div>
        <form v-show="controlTab === 'generate'" id="color-generation-panel" class="control-panel"
          role="tabpanel" aria-labelledby="color-generation-tab" @submit.prevent="generate(false)">
          <fieldset :disabled="pending">
            <div class="panel-heading">
              <h2>
                01
                <span>写下你的灵感</span>
              </h2>
              <span class="tiny-label">灵感</span>
            </div>
            <label class="sr-only" for="color-prompt">配色灵感</label>
            <textarea
              id="color-prompt"
              v-model="prompt"
              rows="3"
              maxlength="1000"
              required
              placeholder="比如：雨后的森林，安静、清新…"
            />
            <div class="preset-list">
              <button v-for="preset in presets" :key="preset" type="button" @click="prompt = preset">{{ preset }}</button>
            </div>
            <div class="preference-row">
              <span>明暗</span>
              <div class="segmented">
                <button type="button" :class="{ selected: !dark }" :aria-pressed="!dark" @click="dark = false">浅色</button>
                <button type="button" :class="{ selected: dark }" :aria-pressed="dark" @click="dark = true">深色</button>
              </div>
            </div>
            <div class="preference-row">
              <span>风格</span>
              <div class="segmented">
                <button type="button" :class="{ selected: !vivid }" :aria-pressed="!vivid" @click="vivid = false">柔和</button>
                <button type="button" :class="{ selected: vivid }" :aria-pressed="vivid" @click="vivid = true">鲜明</button>
              </div>
            </div>
            <label class="model-label" for="color-model">生成模型</label>
            <select id="color-model" v-model="modelId">
              <option value="">使用我的默认模型</option>
              <option v-for="model in modelSetting.models" :key="model.id" :value="model.id">
                {{ model.name || model.modelName }}
              </option>
            </select>
            <button
              class="generate-button"
              type="submit"
              :disabled="allLocked || !prompt.trim()"
              data-testid="generate"
            >
              {{ pending ? '正在调配颜色…' : '生成新配色' }}
            </button>
          </fieldset>
          <p v-if="pending" class="control-hint" role="status">正在用你的模型生成方案，请稍候…</p>
          <p v-else-if="allLocked" class="control-hint">全部颜色已锁定，请先解锁至少一个颜色。</p>
          <p v-else class="control-hint">偏好在生成时生效，锁定的颜色会被保留。</p>
          <p v-if="error" class="generation-error" role="alert">
            {{ error }}
            <br>
            当前方案已保留，可以重新生成。
          </p>
        </form>
        <div v-show="controlTab === 'palette'" id="color-palette-panel" class="control-panel palette-panel" role="tabpanel" aria-labelledby="color-palette-tab">
          <div class="panel-heading">
            <h2>
              02
              <span>微调你的颜色</span>
            </h2>
            <span class="tiny-label">{{ locked.length }} / 7 锁定</span>
          </div>
          <p class="panel-hint">点色块调色，锁定喜欢的颜色再生成。</p>
          <fieldset :disabled="pending">
            <div v-for="role in COLOR_ROLES" :key="role.key" class="color-row">
              <input
                class="color-picker"
                type="color"
                :value="current.palette[role.key]"
                :aria-label="`选择${role.label}`"
                :disabled="locked.includes(role.key)"
                @input="updateColor(role.key, $event)"
              >
              <label :for="`hex-${role.key}`">{{ role.label }}</label>
              <input
                :id="`hex-${role.key}`"
                class="hex-input"
                :value="current.palette[role.key]"
                :aria-label="`${role.label}色值`"
                maxlength="7"
                spellcheck="false"
                :disabled="locked.includes(role.key)"
                @input="updateColor(role.key, $event)"
                @change="updateColor(role.key, $event)"
              >
              <button
                type="button"
                class="lock-button"
                :class="{ locked: locked.includes(role.key) }"
                :aria-label="`${locked.includes(role.key) ? '解锁' : '锁定'}${role.label}`"
                :aria-pressed="locked.includes(role.key)"
                @click="toggleLock(role.key)"
              >
                <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
                  <rect x="4.5" y="8.5" width="11" height="8" rx="2" />
                  <path v-if="locked.includes(role.key)" d="M7 8V6a3 3 0 0 1 6 0v2" />
                  <path v-else d="M7 8V6a3 3 0 0 1 6-1" />
                  <path d="M10 12v1" />
                </svg>
              </button>
            </div>
          </fieldset>
        </div>
      </aside>
      <div class="lab-results" :aria-busy="pending">
        <div class="preview-heading">
          <div>
            <span class="live-dot" />
            <strong>实时预览</strong>
            <span class="source-tag">{{ source }}</span>
          </div>
          <button type="button" :disabled="pending" @click="baseline = copyTheme(current)">固定为对比方案</button>
        </div>
        <div class="preview-grid" :class="{ comparing: baseline }">
          <article v-if="baseline" class="theme-frame">
            <div class="theme-caption">
              <span>对比 · {{ baseline.name }}</span>
              <button type="button" aria-label="关闭方案对比" @click="baseline = undefined"><el-icon><Close /></el-icon></button>
            </div>
            <ColorThemePreview :theme="baseline" compact />
          </article>
          <article class="theme-frame">
            <div v-if="baseline" class="theme-caption">
              <span>当前 · {{ current.name }}</span>
            </div>
            <ColorThemePreview :theme="current" :compact="!!baseline" />
          </article>
        </div>
        <div class="theme-summary">
          <div>
            <h2>{{ current.name }}</h2>
            <p>{{ current.description }}</p>
          </div>
          <div class="mini-palette" aria-hidden="true">
            <i v-for="role in COLOR_ROLES" :key="role.key" :style="{ background: current.palette[role.key] }" />
          </div>
        </div>
        <section class="contrast-panel">
          <div class="contrast-heading">
            <div>
              <h2>
                文字可读性
                <span :class="{ warning: passingCount < contrast.length }">
                  {{ passingCount }}
                  /
                  {{ contrast.length }}
                  达标
                </span>
              </h2>
              <p>根据当前色值实时计算 · 普通文字对比度目标 ≥ 4.5:1</p>
            </div>
            <button
              type="button"
              :disabled="pending || allLocked || !prompt.trim()"
              data-testid="improve"
              @click="generate(true)"
            >
              优化可读性
            </button>
          </div>
          <div class="contrast-checks">
            <div v-for="item in contrast" :key="item.label" :class="{ failing: !item.passes }">
              <span>{{ item.label }}</span>
              <strong>
                {{ item.ratio.toFixed(2) }}
                :1
                <el-icon :aria-label="item.passes ? '达标' : '待调整'"><Check v-if="item.passes" /><Warning v-else /></el-icon>
              </strong>
            </div>
          </div>
          <p v-if="passingCount < contrast.length" class="contrast-note">部分文字对比度不足。可以手动调色或让 AI 优化；锁定颜色可能限制优化结果。</p>
        </section>
        <section v-if="history.length" class="history-panel">
          <div class="history-heading">
            <h2>最近方案</h2>
            <span>本页保留最近 6 个，刷新后清空</span>
          </div>
          <div class="history-list">
            <button
              v-for="(theme, index) in history"
              :key="index"
              type="button"
              :disabled="pending"
              @click="restoreTheme(theme)"
            >
              <span class="history-colors">
                <i v-for="role in COLOR_ROLES" :key="role.key" :style="{ background: theme.palette[role.key] }" />
              </span>
              <span>{{ theme.name }}</span>
            </button>
          </div>
        </section>
        <details class="css-panel">
          <summary>查看当前 CSS 变量</summary>
          <pre>{{ css }}</pre>
        </details>
      </div>
    </div>
  </section>
</template>

<style scoped>
.color-lab {
  padding: var(--workspace-gutter);
  overflow-y: auto;
  height: 100%;
}

.lab-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin: 0 auto 24px;
  width: min(var(--workspace-max-width), 100%);
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--text-muted);
  text-decoration: none;
}

.back-link:hover {
  color: var(--text-accent);
}

h1 {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 28px;
  margin: 12px 0 8px;
  font-weight: 500;
}

.lab-header p {
  color: var(--text-soft);
  margin: 0;
  font-size: 12px;
}

button {
  cursor: pointer;
  font: inherit;
  transition: background-color 160ms var(--ease), border-color 160ms var(--ease), color 160ms var(--ease);
}

button:disabled {
  cursor: not-allowed;
  opacity: .55;
}

button:focus-visible, input:focus-visible, textarea:focus-visible, select:focus-visible, a:focus-visible, summary:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 2px;
}

.export-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.export-actions button, .preview-heading button, .contrast-heading button {
  border: 1px solid var(--line);
  background: var(--surface);
  color: var(--text-soft);
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 11px;
}

.export-actions button:hover, .preview-heading button:hover, .contrast-heading button:hover {
  border-color: var(--primary-border-strong);
  color: var(--text-accent);
}

.lab-workbench {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 24px;
  width: min(var(--workspace-max-width), 100%);
  margin: 0 auto;
  align-items: start;
}

.lab-controls {
  position: sticky;
  top: 0;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.control-tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  padding: 4px;
  border: 1px solid var(--line);
  border-bottom: 0;
  border-radius: 12px 12px 0 0;
  background: var(--surface);
}

.control-tabs button {
  min-height: 36px;
  border: 0;
  border-radius: 8px;
  color: var(--text-muted);
  background: transparent;
  font-size: 13px;
}

.control-tabs button[aria-selected='true'] {
  color: var(--text);
  background: var(--surface-hover);
}

.lab-controls .control-panel {
  border-radius: 0 0 12px 12px;
}

.export-actions button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.control-panel {
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--surface);
  padding: 20px;
}

fieldset {
  border: 0;
  margin: 0;
  padding: 0;
  min-width: 0;
}

.panel-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

h2 {
  font-size: 13px;
  margin: 0;
}

.panel-heading h2 {
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 500;
}

.panel-heading h2 span {
  color: var(--text);
  margin-left: 8px;
  font-size: 13px;
  font-weight: 500;
}

.tiny-label {
  font-size: 11px;
  color: var(--text-muted);
}

textarea {
  display: block;
  resize: vertical;
  min-height: 84px;
  max-height: 220px;
  width: 100%;
  padding: 12px;
  background: var(--field-bg);
  color: var(--text);
  border: 1px solid var(--line);
  border-radius: 8px;
  font: inherit;
  font-size: 12px;
  line-height: 1.8;
}

.preset-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0 20px;
}

.preset-list button {
  border: 1px solid var(--line-soft);
  background: var(--surface-soft);
  color: var(--text-soft);
  border-radius: 8px;
  padding: 4px 8px;
  font-size: 11px;
}

.preset-list button:hover {
  color: var(--text-accent);
}

.preference-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 12px 0;
  font-size: 11px;
  color: var(--text-soft);
}

.segmented {
  display: flex;
  border-radius: 8px;
  padding: 4px;
  background: var(--surface-soft);
}

.segmented button {
  border: 0;
  background: transparent;
  color: var(--text-soft);
  padding: 4px 16px;
  font-size: 11px;
  border-radius: 4px;
}

.segmented .selected {
  color: var(--text-accent);
  background: var(--surface);
}

.model-label {
  font-size: 11px;
  color: var(--text-muted);
  display: block;
  margin: 20px 0 8px;
}

select {
  width: 100%;
  min-width: 0;
  border: 1px solid var(--line);
  color: var(--text-soft);
  background: var(--field-bg);
  border-radius: 8px;
  padding: 8px;
  font-size: 11px;
}

.generate-button {
  background: var(--button-bg);
  color: var(--button-text);
  width: 100%;
  border: 0;
  border-radius: 8px;
  padding: 12px;
  font-size: 12px;
  margin-top: 16px;
}

.generate-button:hover:not(:disabled) {
  background: var(--button-hover);
}

.control-hint, .panel-hint {
  color: var(--text-muted);
  font-size: 11px;
  line-height: 1.7;
  margin: 12px 0 0;
}

.panel-hint {
  margin: -4px 0 12px;
}

.generation-error {
  background: var(--danger-soft);
  color: var(--danger-text);
  border-radius: 8px;
  padding: 12px;
  line-height: 1.8;
  font-size: 11px;
}

.color-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px solid var(--line-faint);
}

.color-row:last-child {
  border: 0;
  padding-bottom: 0;
}

.color-row label {
  font-size: 11px;
  flex: 1;
  white-space: nowrap;
  color: var(--text-soft);
}

.color-picker {
  width: 24px;
  height: 24px;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  flex-shrink: 0;
}

.color-picker::-webkit-color-swatch-wrapper {
  padding: 0;
}

.color-picker::-webkit-color-swatch {
  border: 1px solid var(--line);
  border-radius: 4px;
}

.hex-input {
  width: 76px;
  border: 1px solid transparent;
  background: transparent;
  color: var(--text-soft);
  font: 11px ui-monospace, monospace;
  padding: 4px 0;
  border-radius: 4px;
}

.hex-input:focus {
  background: var(--field-bg);
}

.lock-button {
  padding: 4px;
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--text-muted);
}

.lock-button svg {
  width: 16px;
  height: 16px;
  stroke: currentColor;
  stroke-width: 1.5;
}

.lock-button.locked {
  background: var(--primary-soft);
  color: var(--text-accent);
}

.lab-results {
  min-width: 0;
}

.preview-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0 0 12px;
  gap: 8px;
}

.preview-heading > div {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.live-dot {
  width: 6px;
  height: 6px;
  background: var(--success);
  border-radius: 50%;
}

.source-tag {
  font-size: 11px;
  color: var(--text-muted);
  background: var(--surface-soft);
  padding: 4px 8px;
  border-radius: 4px;
}

.preview-heading button {
  font-size: 11px;
  padding: 8px 12px;
}

.preview-grid.comparing {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.theme-frame {
  min-width: 0;
}

.theme-caption {
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11px;
  color: var(--text-soft);
}

.theme-caption button {
  background: transparent;
  border: 0;
  color: var(--text-soft);
  font-size: 18px;
}

.theme-summary {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 20px 0 24px;
  align-items: center;
}

.theme-summary h2 {
  font-size: 14px;
  margin-bottom: 8px;
}

.theme-summary p {
  color: var(--text-soft);
  font-size: 11px;
  line-height: 1.8;
  margin: 0;
}

.mini-palette {
  display: flex;
  flex-shrink: 0;
}

.mini-palette i {
  width: 16px;
  height: 28px;
}

.mini-palette i:first-child {
  border-radius: 4px 0 0 4px;
}

.mini-palette i:last-child {
  border-radius: 0 4px 4px 0;
}

.contrast-panel {
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--surface);
  padding: 20px;
}

.contrast-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.contrast-heading h2 span {
  color: var(--success-text);
  font-size: 11px;
  margin-left: 8px;
  font-weight: 500;
}

.contrast-heading h2 span.warning {
  color: var(--warning-text);
}

.contrast-heading p {
  font-size: 11px;
  color: var(--text-muted);
  margin: 8px 0 0;
}

.contrast-heading button {
  font-size: 11px;
  color: var(--text-accent);
  flex-shrink: 0;
}

.contrast-checks {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 8px;
}

.contrast-checks > div {
  padding: 12px;
  border-radius: 8px;
  background: var(--surface-soft);
  font-size: 11px;
  color: var(--text-soft);
}

.contrast-checks strong {
  display: block;
  margin-top: 8px;
  color: var(--success-text);
  font-size: 12px;
  font-weight: 500;
}

.contrast-checks strong .el-icon {
  float: right;
}

.contrast-checks .failing strong {
  color: var(--warning-text);
}

.contrast-note {
  font-size: 11px;
  color: var(--warning-text);
  line-height: 1.8;
  margin: 12px 0 0;
}

.history-panel {
  margin-top: 24px;
}

.history-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.history-heading > span {
  color: var(--text-muted);
  font-size: 11px;
}

.history-list {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  padding-bottom: 8px;
}

.history-list button {
  flex: 0 0 120px;
  padding: 0;
  text-align: left;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface);
  color: var(--text-soft);
  overflow: hidden;
  font-size: 11px;
}

.history-list button > span:last-child {
  display: block;
  padding: 8px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.history-colors {
  display: flex;
  height: 32px;
}

.history-colors i {
  flex: 1;
}

.css-panel {
  margin-top: 20px;
  color: var(--text-muted);
  font-size: 11px;
}

.css-panel summary {
  cursor: pointer;
}

.css-panel pre {
  color: var(--text-code);
  background: var(--code-block-bg);
  border-radius: 8px;
  padding: 16px;
  line-height: 1.8;
  overflow-x: auto;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}

@container workspace (max-width: 1120px) {
  .preview-grid.comparing { grid-template-columns: 1fr; }
  .contrast-heading { align-items: flex-start; flex-wrap: wrap; }
  .theme-summary { gap: 12px; }
  .mini-palette { display: none; }
}

@container workspace (max-width: 840px) {
  .lab-workbench { grid-template-columns: 1fr; }
  .lab-controls { position: static; }
  .lab-header { align-items: flex-start; flex-wrap: wrap; }
}

@media (max-width: 760px) {
  .color-lab { padding: 72px 16px 24px; }
  .lab-header { align-items: flex-start; flex-direction: column; gap: 16px; }
  .lab-header h1 { font-size: 28px; }
  .theme-summary { padding: 20px 0; }
}

@media (prefers-reduced-motion: reduce) {
  button { transition: none; }
}
</style>
