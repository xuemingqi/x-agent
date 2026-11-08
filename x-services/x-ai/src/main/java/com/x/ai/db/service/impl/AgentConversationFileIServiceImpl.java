package com.x.ai.db.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.x.ai.db.entity.AgentConversationFile;
import com.x.ai.db.enums.AgentConversationFileKind;
import com.x.ai.db.mapper.AgentConversationFileMapper;
import com.x.ai.db.service.AgentConversationFileIService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentConversationFileIServiceImpl
        extends ServiceImpl<AgentConversationFileMapper, AgentConversationFile>
        implements AgentConversationFileIService {

    @Override
    public int bindPendingInputs(String userId, String sessionId, List<Long> ids, Long messageId) {
        return baseMapper.update(null, Wrappers.<AgentConversationFile>lambdaUpdate()
                .eq(AgentConversationFile::getUserId, userId)
                .eq(AgentConversationFile::getSessionId, sessionId)
                .eq(AgentConversationFile::getKind, AgentConversationFileKind.INPUT)
                .in(AgentConversationFile::getId, ids)
                .isNull(AgentConversationFile::getMessageId)
                .set(AgentConversationFile::getMessageId, messageId));
    }
}
