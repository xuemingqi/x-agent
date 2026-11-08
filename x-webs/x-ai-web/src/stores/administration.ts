import { defineStore } from 'pinia';
import { computed, ref } from 'vue';

import { getAdministrationStatus } from '@/api/administration';

export const useAdministrationStore = defineStore('administration', () => {
  const loaded = ref(false);
  const loading = ref(false);
  const administrator = ref(false);
  const isAdministrator = computed(() => administrator.value);
  let loadPromise: Promise<void> | undefined;
  let generation = 0;

  async function load(): Promise<void> {
    if (loaded.value) return;
    if (loadPromise) return loadPromise;
    loading.value = true;
    const currentGeneration = generation;
    loadPromise = getAdministrationStatus().then((status) => {
      if (currentGeneration !== generation) return;
      administrator.value = status.administrator;
      loaded.value = true;
    });
    try {
      await loadPromise;
    } finally {
      if (currentGeneration === generation) {
        loading.value = false;
        loadPromise = undefined;
      }
    }
  }

  function reset(): void {
    generation++;
    loadPromise = undefined;
    loading.value = false;
    loaded.value = false;
    administrator.value = false;
  }

  return { isAdministrator, loaded, loading, load, reset };
});
