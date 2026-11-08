import { request } from './http';
import type {
  McpConnectionTest,
  McpUserCredentialPayload,
  McpScope,
  McpServer,
  McpServerPayload,
  PublicMcpServerPayload,
} from '@/types/mcp';

export function listMcpServers(scope?: McpScope): Promise<McpServer[]> {
  return request<McpServer[]>({ url: '/mcps', method: 'GET', params: { scope } });
}
export function createUserMcpServer(payload: McpServerPayload): Promise<McpServer> {
  return request<McpServer>({ url: '/mcps', method: 'POST', data: payload });
}

export function setMcpServerEnabled(server: McpServer, enabled: boolean): Promise<boolean> {
  return request<boolean>({
    url: `/mcps/${server.scope}/${encodeURIComponent(server.id)}/enabled`,
    method: 'PUT', data: { enabled },
  });
}

export function updateUserMcpServer(id: string, payload: McpServerPayload): Promise<McpServer> {
  return request<McpServer>({ url: `/mcps/${encodeURIComponent(id)}`, method: 'PUT', data: payload });
}

export function updateMcpUserCredential(
  id: string,
  payload: McpUserCredentialPayload,
): Promise<McpServer> {
  return request<McpServer>({
    url: `/mcps/internal-public/${encodeURIComponent(id)}/credential`,
    method: 'PUT',
    data: payload,
  });
}

export function deleteUserMcpServer(id: string): Promise<boolean> {
  return request<boolean>({ url: `/mcps/${encodeURIComponent(id)}`, method: 'DELETE' });
}

export function testMcpConnection(scope: McpScope, id: string): Promise<McpConnectionTest> {
  return request<McpConnectionTest>({
    url: `/mcps/${scope}/${encodeURIComponent(id)}/connection-test`,
    method: 'POST',
  });
}

export function listPublicMcpServers(): Promise<McpServer[]> {
  return request<McpServer[]>({ url: '/administration/mcps', method: 'GET' });
}

export function createPublicMcpServer(payload: PublicMcpServerPayload): Promise<McpServer> {
  return request<McpServer>({ url: '/administration/mcps', method: 'POST', data: payload });
}

export function updatePublicMcpServer(id: string, payload: PublicMcpServerPayload): Promise<McpServer> {
  return request<McpServer>({
    url: `/administration/mcps/${encodeURIComponent(id)}`, method: 'PUT', data: payload,
  });
}

export function deletePublicMcpServer(id: string): Promise<boolean> {
  return request<boolean>({ url: `/administration/mcps/${encodeURIComponent(id)}`, method: 'DELETE' });
}
