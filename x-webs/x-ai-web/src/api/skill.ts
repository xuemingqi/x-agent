import { request, requestBlob } from './http';
import type { AgentSkill, SkillArchiveFormat, UserSkillPayload } from '@/types/skill';

export function listSkills(): Promise<AgentSkill[]> {
  return request<AgentSkill[]>({ url: '/skills', method: 'GET' });
}

export function setSkillEnabled(name: string, enabled: boolean): Promise<boolean> {
  return request<boolean>({
    url: `/skills/${encodeURIComponent(name)}/enabled`,
    method: 'PUT', data: { enabled },
  });
}

export function createUserSkill(payload: UserSkillPayload): Promise<AgentSkill> {
  return request<AgentSkill>({ url: '/skills', method: 'POST', data: payload });
}

export function importSkillArchive(file: File): Promise<AgentSkill> {
  const data = new FormData();
  data.append('file', file);
  return request<AgentSkill>({ url: '/skills/import', method: 'POST', data, timeout: 240000 });
}

export function exportSkillArchive(name: string, format: SkillArchiveFormat): Promise<Blob> {
  return requestBlob({
    url: `/skills/${encodeURIComponent(name)}/export`,
    method: 'GET', params: { format }, timeout: 60000,
  });
}

export function updateUserSkill(name: string, payload: UserSkillPayload): Promise<AgentSkill> {
  return request<AgentSkill>({ url: `/skills/${encodeURIComponent(name)}`, method: 'PUT', data: payload });
}

export function deleteUserSkill(name: string): Promise<boolean> {
  return request<boolean>({ url: `/skills/${encodeURIComponent(name)}`, method: 'DELETE' });
}
