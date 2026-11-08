import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { currentColorMode, initializeColorMode, toggleColorMode } from './colorMode';

const storageGetItem = vi.fn<(key: string) => string | null>();
const storageSetItem = vi.fn<(key: string, value: string) => void>();
const classListToggle = vi.fn<(token: string, force?: boolean) => boolean>();
const themeColorSetAttribute = vi.fn<(name: string, value: string) => void>();
const rootDataset: Record<string, string> = {};

beforeEach(() => {
  storageGetItem.mockReturnValue(null);
  vi.stubGlobal('localStorage', {
    getItem: storageGetItem,
    setItem: storageSetItem,
  });
  vi.stubGlobal('window', {
    matchMedia: vi.fn(() => ({ matches: false })),
  });
  vi.stubGlobal('document', {
    documentElement: {
      classList: { toggle: classListToggle },
      dataset: rootDataset,
    },
    querySelector: vi.fn(() => ({ setAttribute: themeColorSetAttribute })),
  });
});

afterEach(() => {
  vi.clearAllMocks();
  vi.unstubAllGlobals();
  delete rootDataset.theme;
});

describe('color mode', () => {
  it('applies the persisted mode without writing it again', () => {
    storageGetItem.mockReturnValue('light');

    expect(initializeColorMode()).toBe('light');
    expect(currentColorMode.value).toBe('light');
    expect(classListToggle).toHaveBeenCalledWith('dark', false);
    expect(rootDataset.theme).toBe('light');
    expect(themeColorSetAttribute).toHaveBeenCalledWith('content', '#f7f8fc');
    expect(storageSetItem).not.toHaveBeenCalled();
  });

  it('falls back to the system preference when no valid mode is stored', () => {
    storageGetItem.mockReturnValue('unsupported');
    vi.stubGlobal('window', {
      matchMedia: vi.fn(() => ({ matches: true })),
    });

    expect(initializeColorMode()).toBe('dark');
    expect(currentColorMode.value).toBe('dark');
    expect(classListToggle).toHaveBeenCalledWith('dark', true);
    expect(rootDataset.theme).toBe('dark');
    expect(themeColorSetAttribute).toHaveBeenCalledWith('content', '#0b0d12');
    expect(storageSetItem).not.toHaveBeenCalled();
  });

  it('persists and applies an explicit toggle', () => {
    vi.stubGlobal('window', {
      matchMedia: vi.fn(() => ({ matches: true })),
    });
    initializeColorMode();
    vi.clearAllMocks();

    toggleColorMode();

    expect(currentColorMode.value).toBe('light');
    expect(storageSetItem).toHaveBeenCalledWith('x-ai-color-mode', 'light');
    expect(classListToggle).toHaveBeenCalledWith('dark', false);
    expect(rootDataset.theme).toBe('light');
    expect(themeColorSetAttribute).toHaveBeenCalledWith('content', '#f7f8fc');
  });
});
