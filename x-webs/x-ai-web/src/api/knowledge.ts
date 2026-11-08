import { request, requestBlob } from './http';
import type {
  KnowledgeDocument,
  KnowledgeLink,
  KnowledgeLinkFolder,
  KnowledgeLinkFolderPayload,
  KnowledgeLinkPayload,
} from '@/types/knowledge';

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

export function listPublicKnowledgeDocuments(): Promise<KnowledgeDocument[]> {
  return request<KnowledgeDocument[]>({ url: '/administration/knowledge/documents', method: 'GET' });
}

export function uploadPublicKnowledgeDocuments(files: File[]): Promise<KnowledgeDocument[]> {
  const data = new FormData();
  files.forEach((file) => data.append('files', file));
  return request<KnowledgeDocument[]>({
    url: '/administration/knowledge/documents', method: 'POST', data, timeout: 300000,
  });
}

export function downloadPublicKnowledgeDocument(path: string): Promise<Blob> {
  return requestBlob({
    url: '/administration/knowledge/documents/download', method: 'GET', params: { path }, timeout: 300000,
  });
}

export function deletePublicKnowledgeDocument(path: string): Promise<boolean> {
  return request<boolean>({ url: '/administration/knowledge/documents', method: 'DELETE', params: { path } });
}

export function listPublicKnowledgeLinks(): Promise<KnowledgeLink[]> {
  return request<KnowledgeLink[]>({ url: '/administration/knowledge/links', method: 'GET' });
}

export function createPublicKnowledgeLink(payload: KnowledgeLinkPayload): Promise<KnowledgeLink> {
  return request<KnowledgeLink>({ url: '/administration/knowledge/links', method: 'POST', data: payload });
}

export function updatePublicKnowledgeLink(id: string, payload: KnowledgeLinkPayload): Promise<KnowledgeLink> {
  return request<KnowledgeLink>({
    url: `/administration/knowledge/links/${encodeURIComponent(id)}`, method: 'PUT', data: payload,
  });
}

export function deletePublicKnowledgeLink(id: string): Promise<boolean> {
  return request<boolean>({
    url: `/administration/knowledge/links/${encodeURIComponent(id)}`, method: 'DELETE',
  });
}

export function listPublicKnowledgeLinkFolders(): Promise<KnowledgeLinkFolder[]> {
  return request<KnowledgeLinkFolder[]>({ url: '/administration/knowledge/link-folders', method: 'GET' });
}

export function createPublicKnowledgeLinkFolder(payload: KnowledgeLinkFolderPayload): Promise<KnowledgeLinkFolder> {
  return request<KnowledgeLinkFolder>({
    url: '/administration/knowledge/link-folders', method: 'POST', data: payload,
  });
}

export function renamePublicKnowledgeLinkFolder(id: string, name: string): Promise<KnowledgeLinkFolder> {
  return request<KnowledgeLinkFolder>({
    url: `/administration/knowledge/link-folders/${encodeURIComponent(id)}`, method: 'PUT', data: { name },
  });
}

export function deletePublicKnowledgeLinkFolder(id: string): Promise<boolean> {
  return request<boolean>({
    url: `/administration/knowledge/link-folders/${encodeURIComponent(id)}`, method: 'DELETE',
  });
}
