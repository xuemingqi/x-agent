import { request, requestBlob } from './http';
import type {
  KnowledgeChunkPage,
  KnowledgeDocument,
  KnowledgeLink,
  KnowledgeLinkFolder,
  KnowledgeLinkFolderPayload,
  KnowledgeLinkPayload,
  KnowledgePreview,
  KnowledgeSearchResult,
} from '@/types/knowledge';

export function getKnowledgeDocumentPreview(path: string): Promise<KnowledgePreview> {
  return request<KnowledgePreview>({ url: '/knowledge/documents/preview', method: 'GET', params: { path } });
}

export function listKnowledgeDocumentChunks(path: string, page = 1, size = 20): Promise<KnowledgeChunkPage> {
  return request<KnowledgeChunkPage>({
    url: '/knowledge/documents/chunks', method: 'GET', params: { path, page, size },
  });
}

export function reindexKnowledgeDocument(path: string): Promise<KnowledgeDocument> {
  return request<KnowledgeDocument>({
    url: '/knowledge/documents/reindex', method: 'POST', params: { path }, timeout: 300000,
  });
}

export function searchKnowledgeDocuments(query: string, limit = 6): Promise<KnowledgeSearchResult[]> {
  return request<KnowledgeSearchResult[]>({
    url: '/knowledge/documents/search', method: 'POST', data: { query, limit }, timeout: 300000,
  });
}

export function listKnowledgeDocuments(): Promise<KnowledgeDocument[]> {
  return request<KnowledgeDocument[]>({ url: '/knowledge/documents', method: 'GET' });
}

export function uploadKnowledgeDocuments(files: File[]): Promise<KnowledgeDocument[]> {
  const data = new FormData();
  files.forEach((file) => data.append('files', file));
  return request<KnowledgeDocument[]>({
    url: '/knowledge/documents',
    method: 'POST',
    data,
    timeout: 300000,
  });
}

export function downloadKnowledgeDocument(path: string): Promise<Blob> {
  return requestBlob({
    url: '/knowledge/documents/download',
    method: 'GET',
    params: { path },
    timeout: 300000,
  });
}

export function deleteKnowledgeDocument(path: string): Promise<boolean> {
  return request<boolean>({
    url: '/knowledge/documents',
    method: 'DELETE',
    params: { path },
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
