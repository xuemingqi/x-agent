export const MULTIMODAL_CAPABILITY = {
  NONE: 0,
  IMAGE_INPUT: 1,
  IMAGE_OUTPUT: 2,
  IMAGE_INPUT_OUTPUT: 3,
} as const;

export type MultimodalCapability = typeof MULTIMODAL_CAPABILITY[keyof typeof MULTIMODAL_CAPABILITY];

export interface AgentModel {
  id: string;
  name: string;
  modelName: string;
  modelUrl: string;
  contextWindowSize: number | null;
  temperature: number | null;
  isDefault: boolean;
  multimodalCapability?: MultimodalCapability;
}

export interface ModelSavePayload {
  name: string;
  modelName: string;
  modelUrl: string;
  modelKey?: string;
  contextWindowSize: number;
  temperature: number | null;
  isDefault: boolean;
  multimodalCapability?: MultimodalCapability;
}

export function supportsImageInput(model?: AgentModel): boolean {
  return model?.multimodalCapability === MULTIMODAL_CAPABILITY.IMAGE_INPUT
    || model?.multimodalCapability === MULTIMODAL_CAPABILITY.IMAGE_INPUT_OUTPUT;
}

export function supportsImageOutput(model?: AgentModel): boolean {
  return model?.multimodalCapability === MULTIMODAL_CAPABILITY.IMAGE_OUTPUT
    || model?.multimodalCapability === MULTIMODAL_CAPABILITY.IMAGE_INPUT_OUTPUT;
}
