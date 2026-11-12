package com.x.ai.service.impl;

import com.x.framework.common.utils.UserThreadLocalUtil;

import cn.hutool.core.lang.Snowflake;
import com.x.ai.common.domain.ConversationDo;
import com.x.ai.common.domain.ConversationMessageDo;
import com.x.ai.common.domain.TokenUsageDo;
import com.x.framework.common.utils.JsonUtil;
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
import com.x.ai.service.KnowledgeBaseService;
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

    private final KnowledgeBaseService knowledgeBaseService;

    private final Snowflake snowflake;

    @Override
    public ConversationDo create(List<String> knowledgeBaseIds) {
        String userId = currentUserId();
        List<String> selectedIds = selectedKnowledgeBases(userId, knowledgeBaseIds);
        String sessionId = IdUtil.fastUUID();
        return toConversation(createConversation(userId, sessionId, DEFAULT_TITLE, selectedIds));
    }

    @Override
    public ConversationDo updateKnowledgeBases(String sessionId, List<String> knowledgeBaseIds) {
        String userId = currentUserId();
        AgentConversation conversation = requireConversation(userId, sessionId);
        List<String> selectedIds = selectedKnowledgeBases(userId, knowledgeBaseIds);
        agentConversationIService.updateById(new AgentConversation()
                .setSessionId(sessionId).setKnowledgeBaseIds(selectedIds));
        return toConversation(conversation.setKnowledgeBaseIds(selectedIds));
    }

    @Override
    public List<String> knowledgeBaseIds(String userId, String sessionId) {
        AgentConversation conversation = findConversation(userId, sessionId);
        return conversation == null || conversation.getKnowledgeBaseIds() == null
                ? List.of() : List.copyOf(conversation.getKnowledgeBaseIds());
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
            conversation = createConversation(userId, sessionId, title, List.of());
        }
        Long messageId = saveMessage(userId, sessionId, parentMessageId,
                new ConversationMessageDo()
                        .setRole(ConversationMessageRole.USER)
                        .setContent(content));
        if (!newConversation) {
            // 只更新标题，避免本轮读取的旧快照覆盖并发变更的知识库关联。
            agentConversationIService.updateById(new AgentConversation().setSessionId(sessionId)
                    .setTitle(DEFAULT_TITLE.equals(conversation.getTitle()) ? title : conversation.getTitle()));
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
                .setToolCallId(message.getToolCallId())
                .setTokenUsage(message.getUsage() == null ? null : JsonUtil.toJsonStrOrThrow(message.getUsage())));
        return messageId;
    }

    /**
     * 显式保存对话关联范围，外部隐式创建的会话传空列表，仅检索个人公共知识库。
     */
    private AgentConversation createConversation(String userId, String sessionId, String title,
                                                 List<String> knowledgeBaseIds) {
        AgentConversation conversation = new AgentConversation()
                .setSessionId(sessionId)
                .setUserId(userId)
                .setTitle(title)
                .setKnowledgeBaseIds(knowledgeBaseIds);
        agentConversationIService.save(conversation);
        return conversation;
    }

    /**
     * 查询并校验会话所有者，其他用户的会话不对调用方可见。
     */
    private AgentConversation findConversation(String userId, String sessionId) {
        AgentConversation conversation = agentConversationIService.getById(sessionId);
        if (conversation == null || !userId.equals(conversation.getUserId())) {
            return null;
        }
        return conversation;
    }

    /**
     * 关联变更与详情操作只允许操作本人已有会话。
     */
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

    /**
     * 对外返回关联 ID，公共知识库由运行时自动补充。
     */
    private ConversationDo toConversation(AgentConversation conversation) {
        return new ConversationDo()
                .setSessionId(conversation.getSessionId())
                .setTitle(conversation.getTitle())
                .setKnowledgeBaseIds(conversation.getKnowledgeBaseIds() == null
                        ? List.of() : List.copyOf(conversation.getKnowledgeBaseIds()))
                .setCreatedAt(conversation.getCreateTime() == null ? null : conversation.getCreateTime().toString())
                .setUpdatedAt(conversation.getUpdateTime() == null ? null : conversation.getUpdateTime().toString())
                .setMessages(List.of())
                .setFiles(List.of());
    }

    /**
     * 校验显式关联属于本人，并移除自动关联的公共库和重复项。
     */
    private List<String> selectedKnowledgeBases(String userId, List<String> knowledgeBaseIds) {
        return knowledgeBaseService.requireOwned(userId, knowledgeBaseIds).stream()
                .filter(base -> !base.isDefault()).map(base -> base.getId()).distinct().toList();
    }

    private ConversationMessageDo toMessage(AgentConversationMessage message) {
        return new ConversationMessageDo()
                .setId(String.valueOf(message.getId()))
                .setParentId(message.getParentId() == null ? null : String.valueOf(message.getParentId()))
                .setRole(message.getRole())
                .setContent(message.getContent())
                .setToolCallId(message.getToolCallId())
                .setUsage(message.getTokenUsage() == null ? null
                        : JsonUtil.OBJECT_MAPPER.readValue(message.getTokenUsage(), TokenUsageDo.class))
                .setCreatedAt(message.getCreateTime() == null ? null : message.getCreateTime().toString());
    }

    /**
     * 从可信请求上下文读取当前登录用户 ID。
     */
    private String currentUserId() {
        return UserThreadLocalUtil.getUserId();
    }
}
