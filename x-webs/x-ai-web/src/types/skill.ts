export type SkillArchiveFormat = 'zip' | 'tar' | 'tar.gz';

export interface AgentSkill {
  name: string;
  description: string;
  content: string;
  enabled: boolean;
  resourcePaths: string[];
}

export interface UserSkillPayload {
  name?: string;
  description: string;
  content: string;
}
