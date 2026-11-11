export const MCP_TRANSPORT_TYPE = {
  STREAMABLE_HTTP: 1,
} as const;

export type McpTransportType = (typeof MCP_TRANSPORT_TYPE)[keyof typeof MCP_TRANSPORT_TYPE];

export interface McpServer {
  id: string;
  name: string;
  description: string;
  transportType: McpTransportType;
  endpoint?: string | null;
  headers: Record<string, string>;
  queryParameters: Record<string, string>;
  enabled: boolean;
  runtimeSupported: boolean;
  requestTimeoutSeconds: number;
  initializationTimeoutSeconds: number;
}
export interface McpServerPayload {
  name?: string;
  description: string;
  transportType: McpTransportType;
  endpoint: string;
  headers: Record<string, string>;
  queryParameters: Record<string, string>;
  enabled: boolean;
  requestTimeoutSeconds: number;
  initializationTimeoutSeconds: number;
}

export interface McpConnectionTest {
  connected: boolean;
  toolNames: string[];
}
