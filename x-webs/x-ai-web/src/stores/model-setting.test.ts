import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { createModel, listModels, updateModel } from '@/api/model';
import { MULTIMODAL_CAPABILITY, type AgentModel, type ModelSavePayload } from '@/types/model';
import { useModelSettingStore } from './model-setting';

vi.mock('@/api/model', () => ({
  listModels: vi.fn(), createModel: vi.fn(), updateModel: vi.fn(), deleteModel: vi.fn(),
}));

function model(id: string, isDefault = false): AgentModel {
  return {
    id, isDefault, name: id, modelName: id, modelUrl: 'https://example.com/v1',
    temperature: null, contextWindowSize: 131072,
  };
}

describe('personal model settings', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setActivePinia(createPinia());
  });

  it('uses the user default even when it is not the first returned model', async () => {
    vi.mocked(listModels).mockResolvedValue([model('first'), model('default', true)]);
    const store = useModelSettingStore();
    await store.initialize();
    expect(store.defaultModelId).toBe('default');
    expect(store.requireConfigured()).toBe(true);
    expect(store.dialogVisible).toBe(false);
  });

  it('opens configuration only when the current user has no models', async () => {
    vi.mocked(listModels).mockResolvedValue([]);
    const store = useModelSettingStore();
    await store.initialize();
    expect(store.configured).toBe(false);
    expect(store.requireConfigured()).toBe(false);
    expect(store.dialogVisible).toBe(true);
  });

  it('does not reuse a previous user model response after signing out', async () => {
    let resolvePrevious: (models: AgentModel[]) => void = () => {};
    vi.mocked(listModels).mockReturnValueOnce(new Promise((resolve) => { resolvePrevious = resolve; }));
    const store = useModelSettingStore();
    const previousLoad = store.initialize();
    store.reset();
    vi.mocked(listModels).mockResolvedValueOnce([model('current', true)]);
    await store.initialize();
    resolvePrevious([model('previous', true)]);
    await previousLoad;
    expect(store.models.map(({ id }) => id)).toEqual(['current']);
    expect(store.defaultModelId).toBe('current');
  });

  it('saves image capabilities through personal model creation and editing', async () => {
    const saved = { ...model('mine', true), multimodalCapability: MULTIMODAL_CAPABILITY.IMAGE_INPUT_OUTPUT };
    const payload: ModelSavePayload = {
      name: saved.name, modelName: saved.modelName, modelUrl: saved.modelUrl,
      modelKey: 'personal-key', contextWindowSize: 131072, temperature: null,
      isDefault: true, multimodalCapability: saved.multimodalCapability,
    };
    vi.mocked(createModel).mockResolvedValue(saved);
    vi.mocked(updateModel).mockResolvedValue(saved);
    vi.mocked(listModels).mockResolvedValue([saved]);
    const store = useModelSettingStore();

    await store.save(payload);
    await store.save({ ...payload, modelKey: undefined }, saved.id);

    expect(createModel).toHaveBeenCalledExactlyOnceWith(payload);
    expect(updateModel).toHaveBeenCalledExactlyOnceWith(saved.id, { ...payload, modelKey: undefined });
    expect(store.models[0].multimodalCapability).toBe(MULTIMODAL_CAPABILITY.IMAGE_INPUT_OUTPUT);
  });
});
