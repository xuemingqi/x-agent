package com.x.ai.db.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.x.ai.db.entity.AgentConversationFile;

import java.util.List;

public interface AgentConversationFileIService extends IService<AgentConversationFile> {

    /**
     * 仅绑定指定用户、会话下尚未使用的输入附件，返回实际绑定数量。
     */
    int bindPendingInputs(String userId, String sessionId, List<Long> ids, Long messageId);
}
