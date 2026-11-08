import { request } from './http';
import type { AgentModel, ModelSavePayload } from '@/types/model';

export function listModels(): Promise<AgentModel[]> {
  return request<AgentModel[]>({
    url: '/models',
    method: 'GET',
  });
}

export function createModel(payload: ModelSavePayload): Promise<AgentModel> {
  return request({ url: '/models', method: 'POST', data: payload });
}

export function updateModel(id: string, payload: ModelSavePayload): Promise<AgentModel> {
  return request({ url: `/models/${encodeURIComponent(id)}`, method: 'PUT', data: payload });
}

export function deleteModel(id: string): Promise<void> {
  return request({ url: `/models/${encodeURIComponent(id)}`, method: 'DELETE' });
}
