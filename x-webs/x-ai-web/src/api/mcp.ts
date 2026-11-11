import { request } from './http';
import type { McpConnectionTest, McpServer, McpServerPayload } from '@/types/mcp';

export function listMcpServers(): Promise<McpServer[]> {
  return request<McpServer[]>({ url: '/mcps', method: 'GET' });
}

export function createUserMcpServer(payload: McpServerPayload): Promise<McpServer> {
  return request<McpServer>({ url: '/mcps', method: 'POST', data: payload });
}

export function setMcpServerEnabled(id: string, enabled: boolean): Promise<boolean> {
  return request<boolean>({
    url: `/mcps/${encodeURIComponent(id)}/enabled`,
    method: 'PUT', data: { enabled },
  });
}

export function updateUserMcpServer(id: string, payload: McpServerPayload): Promise<McpServer> {
  return request<McpServer>({ url: `/mcps/${encodeURIComponent(id)}`, method: 'PUT', data: payload });
}

export function deleteUserMcpServer(id: string): Promise<boolean> {
  return request<boolean>({ url: `/mcps/${encodeURIComponent(id)}`, method: 'DELETE' });
}

export function testMcpConnection(id: string): Promise<McpConnectionTest> {
  return request<McpConnectionTest>({ url: `/mcps/${encodeURIComponent(id)}/connection-test`, method: 'POST' });
}
