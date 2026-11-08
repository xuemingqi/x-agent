package com.x.ai.service.impl;

import com.x.framework.common.utils.UserThreadLocalUtil;

import cn.hutool.core.lang.Snowflake;
import com.x.ai.common.domain.ConversationDo;
import com.x.ai.common.domain.ConversationMessageDo;
import com.x.ai.db.entity.AgentConversation;
import com.x.ai.db.entity.AgentConversationMessage;
import com.x.ai.db.enums.ConversationMessageRole;
import com.x.ai.db.service.AgentConversationIService;
import com.x.ai.db.service.AgentConversationMessageIService;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.IdUtil;
import com.x.ai.service.AgentScopeStoreService;
import com.x.ai.service.ConversationFileService;
import com.x.ai.service.ConversationService;
import io.agentscope.core.state.AgentStateStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private static final String DEFAULT_TITLE = "新对话";

    private static final int TITLE_LENGTH = 100;

    private final AgentConversationIService agentConversationIService;

    private final AgentConversationMessageIService agentConversationMessageIService;

    private final AgentScopeStoreService agentScopeStoreService;

    private final ConversationFileService conversationFileService;


    private final Snowflake snowflake;

    @Override
    public ConversationDo create() {
        String sessionId = IdUtil.fastUUID();
        AgentConversation conversation = createConversation(currentUserId(), sessionId, DEFAULT_TITLE);
        return toConversation(conversation);
    }

    @Override
    public List<ConversationDo> list() {
        String userId = currentUserId();
        return agentConversationIService.lambdaQuery()
                .eq(AgentConversation::getUserId, userId)
                .orderByDesc(AgentConversation::getUpdateTime)
                .list()
                .stream()
                .map(this::toConversation)
                .toList();
    }

    @Override
    public ConversationDo get(String sessionId) {
        String userId = currentUserId();
        AgentConversation conversation = requireConversation(userId, sessionId);
        List<ConversationMessageDo> messages = agentConversationMessageIService.lambdaQuery()
                .eq(AgentConversationMessage::getUserId, userId)
                .eq(AgentConversationMessage::getSessionId, sessionId)
                .orderByAsc(AgentConversationMessage::getId)
                .list()
                .stream()
                .map(this::toMessage)
                .toList();
        return toConversation(conversation)
                .setMessages(messages)
                .setFiles(conversationFileService.list(userId, sessionId));
    }

    @Override
    public boolean delete(String sessionId) {
        String userId = currentUserId();
        AgentConversation conversation = findConversation(userId, sessionId);
        AgentStateStore stateStore = agentScopeStoreService.createRuntimeStore().agentStateStore();
        boolean existed = conversation != null || stateStore.exists(userId, sessionId);
        if (conversation != null) {
            conversationFileService.deleteConversationFiles(userId, sessionId);
            agentConversationMessageIService.lambdaUpdate()
                    .eq(AgentConversationMessage::getUserId, userId)
                    .eq(AgentConversationMessage::getSessionId, sessionId)
                    .remove();
            agentConversationIService.removeById(sessionId);
        }
        stateStore.delete(userId, sessionId);
        return existed;
    }

    @Override
    @Transactional
    public Long appendUserMessage(String userId, String sessionId, String content) {
        AgentConversation conversation = findConversation(userId, sessionId);
        boolean newConversation = conversation == null;
        Long parentMessageId = newConversation ? null : latestMessageId(userId, sessionId);
        String title = content.length() <= TITLE_LENGTH ? content : content.substring(0, TITLE_LENGTH);
        if (newConversation) {
            conversation = createConversation(userId, sessionId, title);
        }
        Long messageId = saveMessage(userId, sessionId, parentMessageId,
                new ConversationMessageDo()
                        .setRole(ConversationMessageRole.USER)
                        .setContent(content));
        if (!newConversation) {
            if (DEFAULT_TITLE.equals(conversation.getTitle())) {
                conversation.setTitle(title);
            }
            agentConversationIService.updateById(conversation);
        }
        return messageId;
    }

    @Override
    @Async
    @Transactional
    public void appendMessages(String userId, String sessionId, Long parentMessageId,
                               List<ConversationMessageDo> messages) {
        if (CollectionUtils.isEmpty(messages)) {
            return;
        }
        saveMessages(userId, sessionId, parentMessageId, messages);
    }

    /**
     * 按父子顺序保存一组消息。
     */
    private void saveMessages(String userId, String sessionId, Long parentMessageId,
                              List<ConversationMessageDo> messages) {
        Long parentId = parentMessageId;
        for (ConversationMessageDo message : messages) {
            parentId = saveMessage(userId, sessionId, parentId, message);
        }
    }

    /**
     * 保存一条消息并返回消息 ID。
     */
    private Long saveMessage(String userId, String sessionId, Long parentMessageId,
                             ConversationMessageDo message) {
        long messageId = snowflake.nextId();
        agentConversationMessageIService.save(new AgentConversationMessage()
                .setId(messageId)
                .setParentId(parentMessageId)
                .setSessionId(sessionId)
                .setUserId(userId)
                .setRole(message.getRole())
                .setContent(message.getContent())
                .setToolCallId(message.getToolCallId()));
        return messageId;
    }

    private AgentConversation createConversation(String userId, String sessionId, String title) {
        AgentConversation conversation = new AgentConversation()
                .setSessionId(sessionId)
                .setUserId(userId)
                .setTitle(title);
        agentConversationIService.save(conversation);
        return conversation;
    }

    private AgentConversation findConversation(String userId, String sessionId) {
        AgentConversation conversation = agentConversationIService.getById(sessionId);
        if (conversation == null || !userId.equals(conversation.getUserId())) {
            return null;
        }
        return conversation;
    }

    private AgentConversation requireConversation(String userId, String sessionId) {
        AgentConversation conversation = findConversation(userId, sessionId);
        if (conversation == null) {
            throw new XException(ResponseCodeEnum.AGENT_RUNTIME_INVALID);
        }
        return conversation;
    }

    private Long latestMessageId(String userId, String sessionId) {
        List<AgentConversationMessage> messages = agentConversationMessageIService.lambdaQuery()
                .select(AgentConversationMessage::getId)
                .eq(AgentConversationMessage::getUserId, userId)
                .eq(AgentConversationMessage::getSessionId, sessionId)
                .orderByDesc(AgentConversationMessage::getId)
                .last("limit 1")
                .list();
        return CollectionUtils.isEmpty(messages) ? null : messages.getFirst().getId();
    }

    private ConversationDo toConversation(AgentConversation conversation) {
        return new ConversationDo()
                .setSessionId(conversation.getSessionId())
                .setTitle(conversation.getTitle())
                .setCreatedAt(conversation.getCreateTime() == null ? null : conversation.getCreateTime().toString())
                .setUpdatedAt(conversation.getUpdateTime() == null ? null : conversation.getUpdateTime().toString())
                .setMessages(List.of())
                .setFiles(List.of());
    }

    private ConversationMessageDo toMessage(AgentConversationMessage message) {
        return new ConversationMessageDo()
                .setId(String.valueOf(message.getId()))
                .setParentId(message.getParentId() == null ? null : String.valueOf(message.getParentId()))
                .setRole(message.getRole())
                .setContent(message.getContent())
                .setToolCallId(message.getToolCallId())
                .setCreatedAt(message.getCreateTime() == null ? null : message.getCreateTime().toString());
    }

    /**
     * 从可信请求上下文读取当前登录用户 ID。
     */
    private String currentUserId() {
        return UserThreadLocalUtil.getUserId();
    }
}
