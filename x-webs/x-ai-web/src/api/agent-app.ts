import { request } from '@/api/http';
import { isAgentAppDataKey } from '@/types/agent-app';
import type { AgentApp, AgentAppDetail, AgentAppOperation, AgentAppSave, AgentAppVersion } from '@/types/agent-app';

export function listAgentApps(): Promise<AgentApp[]> {
  return request({ url: '/apps', method: 'GET' });
}

export function getAgentApp(id: string): Promise<AgentAppDetail> {
  return request({ url: `/apps/${encodeURIComponent(id)}`, method: 'GET' });
}

export function saveAgentApp(payload: AgentAppSave, id?: string): Promise<AgentAppDetail> {
  return request({ url: id ? `/apps/${encodeURIComponent(id)}` : '/apps', method: id ? 'PUT' : 'POST', data: payload });
}

export function listAgentAppVersions(id: string): Promise<AgentAppVersion[]> {
  return request({ url: `/apps/${encodeURIComponent(id)}/versions`, method: 'GET' });
}

export function activateAgentAppVersion(id: string, version: string, baseVersion: string): Promise<AgentAppDetail> {
  return request({ url: `/apps/${encodeURIComponent(id)}/versions/${encodeURIComponent(version)}/activate`,
    method: 'POST', data: { baseVersion } });
}

export function deleteAgentApp(id: string): Promise<boolean> {
  return request({ url: `/apps/${encodeURIComponent(id)}`, method: 'DELETE' });
}

/** 身份凭据只由宿主请求层附加，不进入应用页面。 */
export function executeAgentAppOperation(id: string, version: string, operation: AgentAppOperation,
  payload: Record<string, unknown>, signal: AbortSignal): Promise<unknown> {
  const base = `/apps/${encodeURIComponent(id)}`;
  if (operation === 'http.request') {
    return request({ url: `${base}/runtime/http`, method: 'POST', data: { version, request: payload },
      timeout: 40000, signal });
  }
  if (operation === 'model.generate' || operation === 'agent.run') {
    return request({ url: `${base}/runtime/${operation === 'model.generate' ? 'generate' : 'run'}`,
      method: 'POST', data: { ...payload, version }, timeout: 180000, signal });
  }
  if (!isAgentAppDataKey(payload.key)) throw new Error('应用数据键无效');
  const url = `${base}/data/${encodeURIComponent(payload.key)}`;
  return request({ url, method: operation === 'storage.get' ? 'GET' : operation === 'storage.set' ? 'PUT' : 'DELETE',
    params: { version }, data: operation === 'storage.set' ? { value: payload.value } : undefined, signal });
}
