import { describe, expect, it } from 'vitest';

import { MCP_SCOPE, MCP_TRANSPORT_TYPE } from '@/types/mcp';
import type { McpServer } from '@/types/mcp';
import { mergeMcpManagementServers } from './mcpManagement';

function server(id: string, overrides: Partial<McpServer> = {}): McpServer {
  return {
    id, name: id, description: id, scope: MCP_SCOPE.INTERNAL_PUBLIC,
    transportType: MCP_TRANSPORT_TYPE.STREAMABLE_HTTP,
    arguments: [], headers: {}, queryParameters: {}, environment: {},
    enabled: true, userEnabled: true, editable: false, runtimeSupported: true,
    userCredentialConfigured: false, userCredentialRequired: true,
    requestTimeoutSeconds: 60, initializationTimeoutSeconds: 30,
    ...overrides,
  };
}

describe('MCP management list', () => {
  it('retains the user credential state while using editable administrator configuration', () => {
    const personal = server('personal', { scope: MCP_SCOPE.USER, editable: true });
    const visible = server('shared', { userCredentialConfigured: true });
    const managed = server('shared', {
      editable: true, endpoint: 'https://example.com/mcp',
      headers: { Authorization: '' }, userCredentialConfigured: false,
    });

    const merged = mergeMcpManagementServers([personal, visible], [managed]);

    expect(merged).toEqual([personal, { ...managed, userCredentialConfigured: true }]);
    expect(managed.userCredentialConfigured).toBe(false);
  });

  it('keeps the actual service switch without inventing a missing personal credential', () => {
    const visible = server('unconfigured', { enabled: false });
    const managed = server('unconfigured', { enabled: true, editable: true });
    const unavailable = server('unavailable', { enabled: false, editable: true });

    const merged = mergeMcpManagementServers([visible], [managed, unavailable]);

    expect(merged).toEqual([managed, unavailable]);
    expect(merged.every((entry) => !entry.userCredentialConfigured)).toBe(true);
  });

  it('retains the current user switch returned by administrator configuration', () => {
    const visible = server('shared');
    const managed = server('shared', { editable: true, userEnabled: false });

    expect(mergeMcpManagementServers([visible], [managed])[0].userEnabled).toBe(false);
    expect(managed.userEnabled).toBe(false);
  });
});
