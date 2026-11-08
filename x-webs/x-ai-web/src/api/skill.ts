import { request, requestBlob } from './http';
import type { AgentSkill, SkillArchiveFormat, SkillScope, SkillShare, UserSkillPayload } from '@/types/skill';

export function listSkills(scope?: SkillScope): Promise<AgentSkill[]> {
  return request<AgentSkill[]>({ url: '/skills', method: 'GET', params: { scope } });
}

export function setSkillEnabled(skill: AgentSkill, enabled: boolean): Promise<boolean> {
  return request<boolean>({
    url: `/skills/${skill.scope}/${encodeURIComponent(skill.name)}/enabled`,
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

export function exportSkillArchive(skill: AgentSkill, format: SkillArchiveFormat): Promise<Blob> {
  return requestBlob({
    url: `/skills/${skill.scope}/${encodeURIComponent(skill.name)}/export`,
    method: 'GET', params: { format }, timeout: 60000,
  });
}

export function updateUserSkill(name: string, payload: UserSkillPayload): Promise<AgentSkill> {
  return request<AgentSkill>({ url: `/skills/${encodeURIComponent(name)}`, method: 'PUT', data: payload });
}

export function deleteUserSkill(name: string): Promise<boolean> {
  return request<boolean>({ url: `/skills/${encodeURIComponent(name)}`, method: 'DELETE' });
}

export function shareUserSkill(name: string, recipientMobile: string): Promise<SkillShare> {
  return request<SkillShare>({
    url: `/skills/${encodeURIComponent(name)}/shares`,
    method: 'POST',
    data: { recipientMobile },
  });
}

export function listPendingSkillShares(): Promise<SkillShare[]> {
  return request<SkillShare[]>({ url: '/skills/shares/incoming', method: 'GET' });
}

export function acceptSkillShare(shareId: string): Promise<boolean> {
  return request<boolean>({ url: `/skills/shares/${encodeURIComponent(shareId)}/accept`, method: 'POST' });
}

export function rejectSkillShare(shareId: string): Promise<boolean> {
  return request<boolean>({ url: `/skills/shares/${encodeURIComponent(shareId)}/reject`, method: 'POST' });
}

export function listPublicSkills(): Promise<AgentSkill[]> {
  return request<AgentSkill[]>({ url: '/administration/skills', method: 'GET' });
}

export function createPublicSkill(payload: UserSkillPayload): Promise<AgentSkill> {
  return request<AgentSkill>({ url: '/administration/skills', method: 'POST', data: payload });
}

export function updatePublicSkill(name: string, payload: UserSkillPayload): Promise<AgentSkill> {
  return request<AgentSkill>({
    url: `/administration/skills/${encodeURIComponent(name)}`, method: 'PUT', data: payload,
  });
}

export function deletePublicSkill(name: string): Promise<boolean> {
  return request<boolean>({ url: `/administration/skills/${encodeURIComponent(name)}`, method: 'DELETE' });
}
