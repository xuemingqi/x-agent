import { describe, expect, it } from 'vitest';

import { contrastRatio, copyTheme, exportThemeCss, INITIAL_COLOR_THEME, isHexColor, paletteContrast } from './color-theme';

describe('color theme calculations', () => {
  it('computes WCAG reference contrast without depending on model claims', () => {
    expect(contrastRatio('#000000', '#FFFFFF')).toBe(21);
    expect(contrastRatio('#777777', '#777777')).toBe(1);
    expect(contrastRatio('#777777', '#FFFFFF')).toBeCloseTo(4.478, 3);
    const theme = copyTheme(INITIAL_COLOR_THEME);
    theme.palette.text = theme.palette.surface;
    expect(paletteContrast(theme.palette).find((item) => item.label === '正文 / 卡片')?.passes).toBe(false);
  });

  it('exports the currently edited seven slots and keeps comparison snapshots independent', () => {
    const current = copyTheme(INITIAL_COLOR_THEME);
    const baseline = copyTheme(current);
    current.palette.primary = '#123456';
    expect(baseline.palette.primary).toBe('#346650');
    const css = exportThemeCss(current.palette);
    expect(css).toContain('--color-primary: #123456;');
    expect(css).toContain('--color-on-primary: #FFFFFF;');
    expect(css.match(/--color-/g)).toHaveLength(7);
    expect(isHexColor('#ABCDEF')).toBe(true);
    expect(isHexColor('#abc')).toBe(false);
    expect(isHexColor('red; background: url(test)')).toBe(false);
  });
});
