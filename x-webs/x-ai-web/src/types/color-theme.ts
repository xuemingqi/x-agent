export const COLOR_ROLES = [
  { key: 'background', label: '页面背景', variable: 'background' },
  { key: 'surface', label: '卡片背景', variable: 'surface' },
  { key: 'primary', label: '主题色', variable: 'primary' },
  { key: 'onPrimary', label: '按钮文字', variable: 'on-primary' },
  { key: 'text', label: '正文', variable: 'text' },
  { key: 'mutedText', label: '辅助文字', variable: 'muted-text' },
  { key: 'border', label: '边框', variable: 'border' },
] as const;

export type ColorRole = typeof COLOR_ROLES[number]['key'];
export type ColorPalette = Record<ColorRole, string>;

export interface ColorTheme {
  name: string;
  description: string;
  palette: ColorPalette;
}

export interface ColorThemeGenerateRequest {
  prompt: string;
  modelId?: string;
  dark: boolean;
  vivid: boolean;
  improveReadability: boolean;
  currentPalette: ColorPalette;
  lockedColors: Partial<ColorPalette>;
}
