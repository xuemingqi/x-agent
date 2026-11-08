import { readonly, ref } from 'vue';

export type ColorMode = 'light' | 'dark';

const COLOR_MODE_STORAGE_KEY = 'x-ai-color-mode';
const DARK_MODE_MEDIA_QUERY = '(prefers-color-scheme: dark)';
const THEME_COLORS: Record<ColorMode, string> = {
  light: '#f7f8fc',
  dark: '#0b0d12',
};

const activeColorMode = ref<ColorMode>('dark');

export const currentColorMode = readonly(activeColorMode);

export function initializeColorMode(): ColorMode {
  const colorMode = getStoredColorMode() ?? getSystemColorMode();
  applyColorMode(colorMode);
  return colorMode;
}

export function setColorMode(colorMode: ColorMode): void {
  applyColorMode(colorMode);
  localStorage.setItem(COLOR_MODE_STORAGE_KEY, colorMode);
}

export function toggleColorMode(): void {
  setColorMode(activeColorMode.value === 'dark' ? 'light' : 'dark');
}

function getStoredColorMode(): ColorMode | null {
  const storedColorMode = localStorage.getItem(COLOR_MODE_STORAGE_KEY);
  return storedColorMode === 'light' || storedColorMode === 'dark' ? storedColorMode : null;
}

function getSystemColorMode(): ColorMode {
  return window.matchMedia(DARK_MODE_MEDIA_QUERY).matches ? 'dark' : 'light';
}

function applyColorMode(colorMode: ColorMode): void {
  activeColorMode.value = colorMode;
  document.documentElement.classList.toggle('dark', colorMode === 'dark');
  document.documentElement.dataset.theme = colorMode;
  document.querySelector<HTMLMetaElement>('meta[name="theme-color"]')
    ?.setAttribute('content', THEME_COLORS[colorMode]);
}
