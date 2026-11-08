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
