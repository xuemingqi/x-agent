export interface KnowledgeDocument {
  path: string;
  name: string;
  size: number;
  contentType: string;
  updateTime: string;
}

export const KNOWLEDGE_LINK_SCOPE = {
  PUBLIC: 1,
  USER: 2,
} as const;

export type KnowledgeLinkScope = (typeof KNOWLEDGE_LINK_SCOPE)[keyof typeof KNOWLEDGE_LINK_SCOPE];

export const KNOWLEDGE_ROOT_FOLDER_ID = '0';

export interface KnowledgeLinkFolder {
  id: string;
  parentId: string;
  name: string;
  scope: KnowledgeLinkScope;
  editable: boolean;
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
  scope: KnowledgeLinkScope;
  editable: boolean;
  createTime: string;
  updateTime: string;
}

export interface KnowledgeLinkPayload {
  folderId: string;
  name: string;
  summary: string;
  url: string;
}
