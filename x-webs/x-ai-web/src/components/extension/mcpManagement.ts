import { MCP_SCOPE } from '@/types/mcp';
import type { McpServer } from '@/types/mcp';

export function mergeMcpManagementServers(visible: McpServer[], publicServers: McpServer[]): McpServer[] {
  // 管理配置提供编辑字段和真实启用开关，个人列表提供当前账号的凭据状态。
  const visibleServers = new Map(visible.map((server) => [server.id, server]));
  return [
    ...visible.filter((server) => server.scope === MCP_SCOPE.USER),
    ...publicServers.map((server) => ({
      ...server,
      userCredentialConfigured: visibleServers.get(server.id)?.userCredentialConfigured ?? false,
    })),
  ];
}
