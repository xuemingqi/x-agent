export type KnowledgeIndexStatus = 'UNINDEXED' | 'INDEXING' | 'READY' | 'FAILED';

export interface KnowledgeDocument {
  path: string;
  name: string;
  size: number;
  contentType: string;
  updateTime: string;
  status: KnowledgeIndexStatus;
  chunkCount: number;
  embeddingModel: string | null;
  errorMessage: string | null;
}

export interface KnowledgePreview {
  path: string;
  name: string;
  content: string;
  contentType: string;
}

export interface KnowledgeChunk {
  index: number;
  content: string;
  characterCount: number;
}

export interface KnowledgeChunkPage {
  items: KnowledgeChunk[];
  total: number;
  page: number;
  size: number;
}

export interface KnowledgeSearchResult {
  path: string;
  name: string;
  chunkIndex: number;
  content: string;
  score: number;
}

export const KNOWLEDGE_ROOT_FOLDER_ID = '0';

export interface KnowledgeLinkFolder {
  id: string;
  parentId: string;
  name: string;
}

export interface KnowledgeLinkFolderPayload {
  name: string;
  parentId: string;
}

export interface KnowledgeLink {
  id: string;
  folderId: string;
  name: string;
  summary: string;
  url: string;
  createTime: string;
  updateTime: string;
}

export interface KnowledgeLinkPayload {
  folderId: string;
  name: string;
  summary: string;
  url: string;
}
