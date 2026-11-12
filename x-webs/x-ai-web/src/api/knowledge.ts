import { request, requestBlob } from './http';
import type {
  KnowledgeBase,
  KnowledgeBasePayload,
  KnowledgeChunkPage,
  KnowledgeDocument,
  KnowledgeLink,
  KnowledgeLinkFolder,
  KnowledgeLinkFolderPayload,
  KnowledgeLinkPayload,
  KnowledgePreview,
  KnowledgeSearchResult,
} from '@/types/knowledge';

export function listKnowledgeBases(): Promise<KnowledgeBase[]> {
  return request<KnowledgeBase[]>({ url: '/knowledge/bases', method: 'GET' });
}

export function createKnowledgeBase(payload: KnowledgeBasePayload): Promise<KnowledgeBase> {
  return request<KnowledgeBase>({ url: '/knowledge/bases', method: 'POST', data: payload });
}

export function updateKnowledgeBase(id: string, payload: KnowledgeBasePayload): Promise<KnowledgeBase> {
  return request<KnowledgeBase>({ url: `/knowledge/bases/${encodeURIComponent(id)}`, method: 'PUT', data: payload });
}

export function deleteKnowledgeBase(id: string): Promise<boolean> {
  return request<boolean>({ url: `/knowledge/bases/${encodeURIComponent(id)}`, method: 'DELETE' });
}

export function getKnowledgeDocumentPreview(knowledgeBaseId: string, path: string): Promise<KnowledgePreview> {
  return request<KnowledgePreview>({ url: '/knowledge/documents/preview', method: 'GET', params: { knowledgeBaseId, path } });
}

export function listKnowledgeDocumentChunks(knowledgeBaseId: string, path: string, page = 1, size = 20): Promise<KnowledgeChunkPage> {
  return request<KnowledgeChunkPage>({
    url: '/knowledge/documents/chunks', method: 'GET', params: { knowledgeBaseId, path, page, size },
  });
}

export function reindexKnowledgeDocument(knowledgeBaseId: string, path: string): Promise<KnowledgeDocument> {
  return request<KnowledgeDocument>({
    url: '/knowledge/documents/reindex', method: 'POST', params: { knowledgeBaseId, path }, timeout: 300000,
  });
}

export function searchKnowledgeDocuments(knowledgeBaseId: string, query: string, limit = 6): Promise<KnowledgeSearchResult[]> {
  return request<KnowledgeSearchResult[]>({
    url: '/knowledge/documents/search', method: 'POST', data: { knowledgeBaseId, query, limit }, timeout: 300000,
  });
}

export function listKnowledgeDocuments(knowledgeBaseId: string): Promise<KnowledgeDocument[]> {
  return request<KnowledgeDocument[]>({ url: '/knowledge/documents', method: 'GET', params: { knowledgeBaseId } });
}

export function uploadKnowledgeDocuments(knowledgeBaseId: string, files: File[]): Promise<KnowledgeDocument[]> {
  const data = new FormData();
  files.forEach((file) => data.append('files', file));
  return request<KnowledgeDocument[]>({
    url: '/knowledge/documents',
    method: 'POST',
    data,
    params: { knowledgeBaseId },
    timeout: 300000,
  });
}

export function downloadKnowledgeDocument(knowledgeBaseId: string, path: string): Promise<Blob> {
  return requestBlob({
    url: '/knowledge/documents/download',
    method: 'GET',
    params: { knowledgeBaseId, path },
    timeout: 300000,
  });
}

export function deleteKnowledgeDocument(knowledgeBaseId: string, path: string): Promise<boolean> {
  return request<boolean>({
    url: '/knowledge/documents',
    method: 'DELETE',
    params: { knowledgeBaseId, path },
  });
}

export function listKnowledgeLinks(): Promise<KnowledgeLink[]> {
  return request<KnowledgeLink[]>({ url: '/knowledge/links', method: 'GET' });
}

export function createKnowledgeLink(payload: KnowledgeLinkPayload): Promise<KnowledgeLink> {
  return request<KnowledgeLink>({ url: '/knowledge/links', method: 'POST', data: payload });
}

export function updateKnowledgeLink(id: string, payload: KnowledgeLinkPayload): Promise<KnowledgeLink> {
  return request<KnowledgeLink>({
    url: `/knowledge/links/${encodeURIComponent(id)}`,
    method: 'PUT',
    data: payload,
  });
}

export function deleteKnowledgeLink(id: string): Promise<boolean> {
  return request<boolean>({
    url: `/knowledge/links/${encodeURIComponent(id)}`,
    method: 'DELETE',
  });
}

export function listKnowledgeLinkFolders(): Promise<KnowledgeLinkFolder[]> {
  return request<KnowledgeLinkFolder[]>({ url: '/knowledge/link-folders', method: 'GET' });
}

export function createKnowledgeLinkFolder(payload: KnowledgeLinkFolderPayload): Promise<KnowledgeLinkFolder> {
  return request<KnowledgeLinkFolder>({ url: '/knowledge/link-folders', method: 'POST', data: payload });
}

export function renameKnowledgeLinkFolder(id: string, name: string): Promise<KnowledgeLinkFolder> {
  return request<KnowledgeLinkFolder>({
    url: `/knowledge/link-folders/${encodeURIComponent(id)}`,
    method: 'PUT',
    data: { name },
  });
}

export function deleteKnowledgeLinkFolder(id: string): Promise<boolean> {
  return request<boolean>({ url: `/knowledge/link-folders/${encodeURIComponent(id)}`, method: 'DELETE' });
}
