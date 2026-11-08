import { request, requestBlob } from './http';
import type { ConversationFile } from '@/types/chat';

export function uploadConversationFiles(sessionId: string, files: File[]): Promise<ConversationFile[]> {
  const data = new FormData();
  files.forEach((file) => data.append('files', file));
  return request<ConversationFile[]>({
    url: `/conversations/${encodeURIComponent(sessionId)}/files`,
    method: 'POST',
    data,
    timeout: 300000,
  });
}

export function downloadConversationFile(sessionId: string, fileId: string): Promise<Blob> {
  return requestBlob({
    url: `/conversations/${encodeURIComponent(sessionId)}/files/${encodeURIComponent(fileId)}/content`,
    method: 'GET',
    timeout: 300000,
  });
}

export function deletePendingConversationFile(sessionId: string, fileId: string): Promise<boolean> {
  return request<boolean>({
    url: `/conversations/${encodeURIComponent(sessionId)}/files/${encodeURIComponent(fileId)}`,
    method: 'DELETE',
  });
}
