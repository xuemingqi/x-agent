import { COLOR_ROLES, type ColorPalette, type ColorTheme } from '@/types/color-theme';

export const INITIAL_COLOR_THEME: ColorTheme = {
  name: '雨后森林',
  description: '松针的绿，雾气的白。给每一个小小的灵感，留一处安静的位置。',
  palette: {
    background: '#EFF3EE', surface: '#FFFFFF', primary: '#346650', onPrimary: '#FFFFFF',
    text: '#223C2E', mutedText: '#5C6E61', border: '#C8D5C9',
  },
};

export function isHexColor(value: string): boolean {
  return /^#[0-9a-fA-F]{6}$/.test(value);
}

export function copyTheme(theme: ColorTheme): ColorTheme {
  return { ...theme, palette: { ...theme.palette } };
}

export function paletteVariables(palette: ColorPalette): Record<string, string> {
  return Object.fromEntries(COLOR_ROLES.map(({ key, variable }) => [`--color-${variable}`, palette[key]]));
}

export function exportThemeCss(palette: ColorPalette): string {
  return `:root {\n${COLOR_ROLES.map(({ key, variable }) => `  --color-${variable}: ${palette[key]};`).join('\n')}\n}\n`;
}

function luminance(hex: string): number {
  const channels = [1, 3, 5].map((start) => {
    const value = parseInt(hex.slice(start, start + 2), 16) / 255;
    return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
  });
  return channels[0]! * 0.2126 + channels[1]! * 0.7152 + channels[2]! * 0.0722;
}

export function contrastRatio(foreground: string, background: string): number {
  const first = luminance(foreground);
  const second = luminance(background);
  return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
}

export function paletteContrast(palette: ColorPalette) {
  return [
    { label: '正文 / 页面', foreground: palette.text, background: palette.background },
    { label: '正文 / 卡片', foreground: palette.text, background: palette.surface },
    { label: '辅助文字 / 页面', foreground: palette.mutedText, background: palette.background },
    { label: '辅助文字 / 卡片', foreground: palette.mutedText, background: palette.surface },
    { label: '按钮文字 / 主题色', foreground: palette.onPrimary, background: palette.primary },
  ].map((item) => {
    const ratio = contrastRatio(item.foreground, item.background);
    return { label: item.label, ratio, passes: ratio >= 4.5 };
  });
}
