package com.x.ai.service;

import com.x.ai.common.domain.ConversationDo;
import com.x.ai.common.domain.ConversationMessageDo;

import java.util.List;

public interface ConversationService {

    /**
     * 为当前登录用户创建对话，并保存额外关联的个人知识库；公共知识库自动参与检索。
     *
     * @param knowledgeBaseIds 额外关联的知识库 ID，空列表表示仅使用个人公共知识库
     * @return 新建的对话
     */
    ConversationDo create(List<String> knowledgeBaseIds);

    /**
     * 替换当前用户对话额外关联的知识库，校验所有知识库均属于本人。
     */
    ConversationDo updateKnowledgeBases(String sessionId, List<String> knowledgeBaseIds);

    /**
     * 为异步执行读取指定用户的会话知识库快照；新会话返回空列表。
     */
    List<String> knowledgeBaseIds(String userId, String sessionId);

    /**
     * 查询当前登录用户的对话列表。
     *
     * @return 对话列表
     */
    List<ConversationDo> list();

    /**
     * 查询当前登录用户的指定对话。
     *
     * @param sessionId 会话 ID
     * @return 对话详情
     */
    ConversationDo get(String sessionId);

    /**
     * 删除当前登录用户的指定对话及 Agent 状态。
     *
     * @param sessionId 会话 ID
     * @return 删除前是否存在对话或 Agent 状态
     */
    boolean delete(String sessionId);

    /**
     * 记录指定用户在一次 Agent 执行中的用户消息。
     *
     * @param userId 用户 ID
     * @param sessionId 会话 ID
     * @param content 消息内容
     * @return 新建用户消息 ID
     */
    Long appendUserMessage(String userId, String sessionId, String content);

    /**
     * 记录指定用户在一次 Agent 执行中产生的消息。
     *
     * @param userId         用户 ID
     * @param sessionId      会话 ID
     * @param parentMessageId 首条消息的父消息 ID
     * @param messages       消息列表
     */
    void appendMessages(String userId, String sessionId, Long parentMessageId, List<ConversationMessageDo> messages);
}
