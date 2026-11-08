import { describe, expect, it } from 'vitest';

import { MULTIMODAL_CAPABILITY, supportsImageInput, supportsImageOutput } from './model';
import type { AgentModel, MultimodalCapability } from './model';

function model(multimodalCapability?: MultimodalCapability): AgentModel {
  return {
    id: 'personal-model', name: '个人模型', modelName: 'example', modelUrl: 'https://example.test/v1',
    contextWindowSize: 32768, temperature: null, isDefault: true, multimodalCapability,
  };
}

describe('personal model image capabilities', () => {
  it.each([
    [undefined, false, false],
    [MULTIMODAL_CAPABILITY.NONE, false, false],
    [MULTIMODAL_CAPABILITY.IMAGE_INPUT, true, false],
    [MULTIMODAL_CAPABILITY.IMAGE_OUTPUT, false, true],
    [MULTIMODAL_CAPABILITY.IMAGE_INPUT_OUTPUT, true, true],
  ] as const)('maps capability %s to image input/output', (capability, input, output) => {
    expect(supportsImageInput(model(capability))).toBe(input);
    expect(supportsImageOutput(model(capability))).toBe(output);
  });

  it('does not claim capabilities before a personal model is selected', () => {
    expect(supportsImageInput()).toBe(false);
    expect(supportsImageOutput()).toBe(false);
  });
});
