import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { getAdministrationStatus } from '@/api/administration';
import { useAdministrationStore } from './administration';

vi.mock('@/api/administration', () => ({
  getAdministrationStatus: vi.fn(),
}));

describe('administration store', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setActivePinia(createPinia());
  });

  it('shares an in-flight status request between concurrent consumers', async () => {
    let resolveStatus: ((status: { role: 'ADMIN'; administrator: true }) => void) | undefined;
    vi.mocked(getAdministrationStatus).mockImplementation(() => new Promise((resolve) => {
      resolveStatus = resolve;
    }));

    const store = useAdministrationStore();
    const firstLoad = store.load();
    const secondLoad = store.load();

    expect(getAdministrationStatus).toHaveBeenCalledTimes(1);
    resolveStatus?.({ role: 'ADMIN', administrator: true });
    await Promise.all([firstLoad, secondLoad]);

    expect(store.loaded).toBe(true);
    expect(store.isAdministrator).toBe(true);
  });
});
