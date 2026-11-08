export const MCP_SCOPE = {
  PUBLIC: 1,
  USER: 2,
  INTERNAL_PUBLIC: 3,
} as const;

export const MCP_TRANSPORT_TYPE = {
  STREAMABLE_HTTP: 1,
  STDIO: 2,
} as const;

export type McpScope = (typeof MCP_SCOPE)[keyof typeof MCP_SCOPE];
export type McpTransportType = (typeof MCP_TRANSPORT_TYPE)[keyof typeof MCP_TRANSPORT_TYPE];

export interface McpServer {
  id: string;
  name: string;
  description: string;
  scope: McpScope;
  transportType: McpTransportType;
  endpoint?: string;
  command?: string;
  arguments: string[];
  headers: Record<string, string>;
  queryParameters: Record<string, string>;
  environment: Record<string, string>;
  enabled: boolean;
  userEnabled: boolean;
  userCredentialConfigured: boolean;
  userCredentialRequired: boolean;
  editable: boolean;
  runtimeSupported: boolean;
  requestTimeoutSeconds: number;
  initializationTimeoutSeconds: number;
}
export interface McpServerPayload {
  name?: string;
  description: string;
  transportType: McpTransportType;
  endpoint?: string;
  command?: string;
  arguments: string[];
  headers: Record<string, string>;
  queryParameters: Record<string, string>;
  environment: Record<string, string>;
  enabled: boolean;
  requestTimeoutSeconds: number;
  initializationTimeoutSeconds: number;
}

export interface McpConnectionTest {
  connected: boolean;
  toolNames: string[];
}

export interface McpUserCredentialPayload {
  credential: string;
}

export interface PublicMcpServerPayload extends McpServerPayload {
  scope?: McpScope;
}
