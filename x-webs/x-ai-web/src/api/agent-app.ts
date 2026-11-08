import { request } from '@/api/http';
import type { AgentApp } from '@/types/agent-app';

export function listAgentApps(): Promise<AgentApp[]> {
  return request({ url: '/apps', method: 'GET' });
}
