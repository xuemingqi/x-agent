import { request } from './http';
import type { Conversation } from '@/types/chat';

export function createConversation(knowledgeBaseIds: string[] = []): Promise<Conversation> {
  return request<Conversation>({
    url: '/conversations',
    method: 'POST',
    data: { knowledgeBaseIds },
  });
}

export function updateConversationKnowledgeBases(sessionId: string, knowledgeBaseIds: string[]): Promise<Conversation> {
  return request<Conversation>({
    url: `/conversations/${encodeURIComponent(sessionId)}/knowledge-bases`,
    method: 'PUT',
    data: { knowledgeBaseIds },
  });
}

export function listConversations(): Promise<Conversation[]> {
  return request<Conversation[]>({
    url: '/conversations',
    method: 'GET',
  });
}

export function getConversation(sessionId: string): Promise<Conversation> {
  return request<Conversation>({
    url: `/conversations/${encodeURIComponent(sessionId)}`,
    method: 'GET',
  });
}

export function deleteConversation(sessionId: string): Promise<boolean> {
  return request<boolean>({
    url: `/conversations/${encodeURIComponent(sessionId)}`,
    method: 'DELETE',
  });
}
