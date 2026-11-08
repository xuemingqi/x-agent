import { request } from '@/api/http';
import type { ColorTheme, ColorThemeGenerateRequest } from '@/types/color-theme';

export function generateColorTheme(data: ColorThemeGenerateRequest, signal?: AbortSignal): Promise<ColorTheme> {
  return request({ url: '/apps/color-lab/generate', method: 'POST', data, signal, timeout: 150_000 });
}
