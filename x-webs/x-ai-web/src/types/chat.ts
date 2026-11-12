export type MessageRole = 'USER' | 'ASSISTANT' | 'TOOL';

export type AgentEventScope = 'main' | 'subagent';

export const CONVERSATION_FILE_KIND = {
  INPUT: 1,
  OUTPUT: 2,
} as const;

export type ConversationFileKind =
  typeof CONVERSATION_FILE_KIND[keyof typeof CONVERSATION_FILE_KIND];

export interface ConversationFile {
  id: string;
  sessionId: string;
  messageId: string | null;
  kind: ConversationFileKind;
  name: string;
  contentType: string;
  sizeBytes: number;
  workspacePath: string;
  createdAt: string | null;
}

export interface TokenUsage {
  inputTokens: number;
  outputTokens: number;
  totalTokens: number;
  cachedTokens: number;
  cacheCreationTokens: number;
  reasoningTokens: number;
  toolUsePromptTokens: number;
  modelCalls: number;
  timeSeconds: number;
}

export interface ConversationMessage {
  id: string;
  parentId: string | null;
  role: MessageRole;
  content: string;
  toolCallId: string | null;
  usage?: TokenUsage | null;
  createdAt: string | null;
}

export interface Conversation {
  sessionId: string;
  title: string | null;
  /** 显式关联的个人知识库；公共知识库由服务端自动关联。 */
  knowledgeBaseIds: string[];
  createdAt: string | null;
  updatedAt: string | null;
  messages: ConversationMessage[];
  files: ConversationFile[];
}

export interface AgentRequest {
  request: string;
  sessionId: string;
  modelId: string;
  attachmentIds?: string[];
  browserScreenshotEnabled?: boolean;
  context?: Record<string, unknown>;
}

export interface BrowserScreenshotRequest {
  requestId: string;
  taskId: string;
  sessionId: string;
  reason: string;
  previewFile: ConversationFile;
  expiresAt: number;
  /** 请求出现时的处理记录分界，后续内容从该位置之后继续展示。 */
  reasoningOffset: number;
  requestedAt: number;
  status: 'pending' | 'capturing' | 'uploading' | 'declining' | 'received' | 'declined' | 'expired' | 'cancelled';
  files: ConversationFile[];
  error?: string;
}

export interface AgentEvent {
  eventType: string;
  scope?: AgentEventScope;
  source?: string | null;
  taskId: string;
  userId: string;
  sessionId: string;
  modelId?: string;
  generateReason?: string;
  text?: string;
  structuredData: Record<string, unknown>;
}
