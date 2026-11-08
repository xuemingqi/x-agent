import { defineStore } from 'pinia';
import { computed, ref } from 'vue';

import { createModel, deleteModel, listModels, updateModel } from '@/api/model';
import type { AgentModel, ModelSavePayload } from '@/types/model';

export const useModelSettingStore = defineStore('model-setting', () => {
  const models = ref<AgentModel[]>([]);
  const loaded = ref(false);
  const dialogVisible = ref(false);
  let initialization: Promise<void> | undefined;
  let generation = 0;
  const configured = computed(() => models.value.length > 0);
  const defaultModelId = computed(() => models.value.find((model) => model.isDefault)?.id || '');

  async function initialize(force = false): Promise<void> {
    if (initialization) return initialization;
    if (loaded.value && !force) return;
    const currentGeneration = generation;
    initialization = listModels().then((result) => {
      if (currentGeneration !== generation) return;
      models.value = result;
      loaded.value = true;
      if (!result.length) dialogVisible.value = true;
    });
    try {
      await initialization;
    } finally {
      if (currentGeneration === generation) initialization = undefined;
    }
  }

  async function save(payload: ModelSavePayload, id?: string): Promise<AgentModel> {
    const saved = id ? await updateModel(id, payload) : await createModel(payload);
    await initialize(true);
    return saved;
  }

  async function remove(id: string): Promise<void> {
    await deleteModel(id);
    await initialize(true);
  }

  function requireConfigured(): boolean {
    if (configured.value) return true;
    dialogVisible.value = true;
    return false;
  }

  function openDialog(): void {
    dialogVisible.value = true;
  }

  function closeDialog(): void {
    dialogVisible.value = false;
  }

  function reset(): void {
    generation++;
    initialization = undefined;
    models.value = [];
    loaded.value = false;
    dialogVisible.value = false;
  }

  return {
    models, loaded, dialogVisible, configured, defaultModelId,
    initialize, save, remove, requireConfigured, openDialog, closeDialog, reset,
  };
});
