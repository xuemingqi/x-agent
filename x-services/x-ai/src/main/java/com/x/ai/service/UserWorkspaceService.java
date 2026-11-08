package com.x.ai.service;

import java.nio.file.Path;

public interface UserWorkspaceService {

    /**
     * 返回当前用户的知识库共享目录，不存在时创建。
     *
     * @param userId 用户 ID
     * @return 用户知识库绝对路径
     */
    Path knowledgeDirectory(String userId);

}
