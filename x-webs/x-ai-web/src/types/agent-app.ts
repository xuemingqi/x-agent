export type AgentAppPermission = 'model.generate' | 'agent.run' | 'storage' | 'http.request';

export interface AgentApp {
  id: string;
  name: string;
  description: string;
  currentVersion: string | null;
}

export interface AgentAppContent {
  sdkVersion: number;
  html: string;
  css: string;
  source: string;
  permissions: AgentAppPermission[];
}

export interface AgentAppDetail extends AgentApp {
  content: AgentAppContent;
}

export interface AgentAppSave {
  name: string;
  description: string;
  html: string;
  css: string;
  source: string;
  permissions: AgentAppPermission[];
  baseVersion?: string;
}

export interface AgentAppVersion {
  version: string;
  name: string;
  description: string;
  sizeBytes: number;
  sha256: string;
  createTime: string;
  current: boolean;
}

export type AgentAppOperation = 'model.generate' | 'agent.run' | 'http.request' | 'storage.get' | 'storage.set' | 'storage.remove';

/** 点号保留给普通键名；单独的 . 和 .. 会被浏览器归一为路径，不能作为数据键。 */
export function isAgentAppDataKey(value: unknown): value is string {
  return typeof value === 'string' && /^[a-zA-Z0-9_.-]{1,128}$/.test(value) && value !== '.' && value !== '..';
}
