import { request } from './http';

export interface ExternalAccessConfig {
  httpApiUrl: string;
  mcpServerUrl: string;
}

export function getExternalAccessConfig(): Promise<ExternalAccessConfig> {
  return request({ url: '/external-access/config', method: 'get' });
}
