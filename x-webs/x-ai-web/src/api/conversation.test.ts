import { beforeEach, describe, expect, it, vi } from 'vitest';

import { createConversation, updateConversationKnowledgeBases } from './conversation';
import { request } from './http';

vi.mock('./http', () => ({ request: vi.fn().mockResolvedValue({}) }));

describe('conversation knowledge sources API', () => {
  beforeEach(() => vi.clearAllMocks());

  it('creates a conversation with all selected knowledge bases or an empty explicit selection', async () => {
    await createConversation(['rules', 'product']);
    expect(request).toHaveBeenLastCalledWith({
      url: '/conversations', method: 'POST', data: { knowledgeBaseIds: ['rules', 'product'] },
    });
    await createConversation();
    expect(request).toHaveBeenLastCalledWith({
      url: '/conversations', method: 'POST', data: { knowledgeBaseIds: [] },
    });
  });

  it('replaces only the target conversation selection and supports clearing optional sources', async () => {
    await updateConversationKnowledgeBases('session/id', []);
    expect(request).toHaveBeenCalledWith({
      url: '/conversations/session%2Fid/knowledge-bases', method: 'PUT', data: { knowledgeBaseIds: [] },
    });
  });
});
