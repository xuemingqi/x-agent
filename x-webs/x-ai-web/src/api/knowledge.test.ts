import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  createKnowledgeLink, createKnowledgeLinkFolder, deleteKnowledgeLinkFolder,
  listKnowledgeLinkFolders, renameKnowledgeLinkFolder, updateKnowledgeLink,
} from './knowledge';
import { request } from './http';

vi.mock('./http', () => ({ request: vi.fn().mockResolvedValue({}), requestBlob: vi.fn() }));

describe('knowledge folder API', () => {
  beforeEach(() => vi.clearAllMocks());

  it('requests visible folders without supplying user identity or scope', async () => {
    await listKnowledgeLinkFolders();
    expect(request).toHaveBeenCalledWith({ url: '/knowledge/link-folders', method: 'GET' });
  });

  it('keeps large parent and folder IDs as strings through CRUD requests', async () => {
    const id = '2092518874657460224';
    await createKnowledgeLinkFolder({ name: '规则', parentId: id });
    expect(request).toHaveBeenLastCalledWith({
      url: '/knowledge/link-folders', method: 'POST', data: { name: '规则', parentId: id },
    });
    await renameKnowledgeLinkFolder(id, '新规则');
    expect(request).toHaveBeenLastCalledWith({
      url: `/knowledge/link-folders/${id}`, method: 'PUT', data: { name: '新规则' },
    });
    await deleteKnowledgeLinkFolder(id);
    expect(request).toHaveBeenLastCalledWith({ url: `/knowledge/link-folders/${id}`, method: 'DELETE' });
  });

  it('includes the selected folder when creating or moving a document', async () => {
    const payload = { name: '规则', summary: '规则说明', url: 'https://example.com/doc', folderId: '2092518874657460224' };
    await createKnowledgeLink(payload);
    expect(request).toHaveBeenLastCalledWith({ url: '/knowledge/links', method: 'POST', data: payload });
    await updateKnowledgeLink('2092518874657460225', { ...payload, folderId: '0' });
    expect(request).toHaveBeenLastCalledWith({
      url: '/knowledge/links/2092518874657460225', method: 'PUT', data: { ...payload, folderId: '0' },
    });
  });
});

describe('knowledge base and scoped document API', () => {
  beforeEach(() => vi.clearAllMocks());

  it('creates and manages knowledge bases independently of document paths', async () => {
    const { listKnowledgeBases, createKnowledgeBase, updateKnowledgeBase, deleteKnowledgeBase } = await import('./knowledge');
    await listKnowledgeBases();
    expect(request).toHaveBeenLastCalledWith({ url: '/knowledge/bases', method: 'GET' });
    const payload = { name: '产品资料', description: '产品说明和需求' };
    await createKnowledgeBase(payload);
    expect(request).toHaveBeenLastCalledWith({ url: '/knowledge/bases', method: 'POST', data: payload });
    await updateKnowledgeBase('base/1', payload);
    expect(request).toHaveBeenLastCalledWith({ url: '/knowledge/bases/base%2F1', method: 'PUT', data: payload });
    await deleteKnowledgeBase('base/1');
    expect(request).toHaveBeenLastCalledWith({ url: '/knowledge/bases/base%2F1', method: 'DELETE' });
  });

  it('includes the knowledge base in every document read and mutation', async () => {
    const api = await import('./knowledge');
    const { requestBlob } = await import('./http');
    const knowledgeBaseId = 'product';
    const path = 'guide.txt';
    await api.listKnowledgeDocuments(knowledgeBaseId);
    expect(request).toHaveBeenLastCalledWith({ url: '/knowledge/documents', method: 'GET', params: { knowledgeBaseId } });
    await api.getKnowledgeDocumentPreview(knowledgeBaseId, path);
    expect(request).toHaveBeenLastCalledWith({ url: '/knowledge/documents/preview', method: 'GET', params: { knowledgeBaseId, path } });
    await api.listKnowledgeDocumentChunks(knowledgeBaseId, path, 2);
    expect(request).toHaveBeenLastCalledWith({
      url: '/knowledge/documents/chunks', method: 'GET', params: { knowledgeBaseId, path, page: 2, size: 20 },
    });
    await api.reindexKnowledgeDocument(knowledgeBaseId, path);
    expect(request).toHaveBeenLastCalledWith({
      url: '/knowledge/documents/reindex', method: 'POST', params: { knowledgeBaseId, path }, timeout: 300000,
    });
    await api.searchKnowledgeDocuments(knowledgeBaseId, '退款规则');
    expect(request).toHaveBeenLastCalledWith({
      url: '/knowledge/documents/search', method: 'POST', data: { knowledgeBaseId, query: '退款规则', limit: 6 }, timeout: 300000,
    });
    await api.uploadKnowledgeDocuments(knowledgeBaseId, []);
    expect(request).toHaveBeenLastCalledWith({
      url: '/knowledge/documents', method: 'POST', params: { knowledgeBaseId }, data: expect.any(FormData), timeout: 300000,
    });
    await api.downloadKnowledgeDocument(knowledgeBaseId, path);
    expect(requestBlob).toHaveBeenLastCalledWith({
      url: '/knowledge/documents/download', method: 'GET', params: { knowledgeBaseId, path }, timeout: 300000,
    });
    await api.deleteKnowledgeDocument(knowledgeBaseId, path);
    expect(request).toHaveBeenLastCalledWith({ url: '/knowledge/documents', method: 'DELETE', params: { knowledgeBaseId, path } });
  });
});
