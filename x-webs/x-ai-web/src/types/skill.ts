export const SKILL_SCOPE = {
  PUBLIC: 1,
  USER: 2,
  SHARED: 3,
} as const;

export type SkillScope = (typeof SKILL_SCOPE)[keyof typeof SKILL_SCOPE];

export type SkillArchiveFormat = 'zip' | 'tar' | 'tar.gz';

export interface AgentSkill {
  name: string;
  description: string;
  content: string;
  scope: SkillScope;
  editable: boolean;
  enabled: boolean;
  sharedByMobile: string | null;
  resourcePaths: string[];
}

export interface UserSkillPayload {
  name?: string;
  description: string;
  content: string;
}

export const SKILL_SHARE_STATUS = {
  PENDING: 1,
  ACCEPTED: 2,
  REJECTED: 3,
} as const;

export type SkillShareStatus = (typeof SKILL_SHARE_STATUS)[keyof typeof SKILL_SHARE_STATUS];

export interface SkillShare {
  id: string;
  skillName: string;
  description: string;
  senderMobile: string;
  senderDisplayName: string;
  status: SkillShareStatus;
  resourcePaths: string[];
  createTime: string;
}
