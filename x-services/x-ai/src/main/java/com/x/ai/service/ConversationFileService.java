package com.x.ai.service;

import com.x.ai.common.domain.ConversationFileDo;
import com.x.ai.common.domain.ConversationFileDownloadDo;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ConversationFileService {

    /**
     * 将文件上传到当前用户的指定会话。
     *
     * @param sessionId 会话 ID
     * @param files     上传文件
     * @return 已创建的会话文件
     */
    List<ConversationFileDo> upload(String sessionId, List<MultipartFile> files);

    /**
     * 下载当前用户指定会话中的文件。
     *
     * @param sessionId 会话 ID
     * @param fileId    文件 ID
     * @return 文件下载内容
     */
    ConversationFileDownloadDo download(String sessionId, Long fileId);

    /**
     * 获取指定用户会话中的文件元数据；文件不存在或归属不匹配时拒绝访问。
     */
    ConversationFileDo getFile(String userId, String sessionId, Long fileId);

    /**
     * 删除尚未随消息发送的当前用户附件。
     *
     * @param sessionId 会话 ID
     * @param fileId    文件 ID
     * @return 是否删除成功
     */
    boolean deletePending(String sessionId, Long fileId);

    /**
     * 查询指定用户和会话的全部文件。
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @return 按创建顺序排列的文件
     */
    List<ConversationFileDo> list(String userId, String sessionId);

    /**
     * 绑定本轮选择的附件，并将会话输入文件恢复到当前 AgentScope 沙箱。
     *
     * @param userId        用户 ID
     * @param sessionId     会话 ID
     * @param attachmentIds 本轮选择的附件 ID
     * @param userMessageId 本轮用户消息 ID
     * @return 本轮附件及其沙箱路径
     */
    List<ConversationFileDo> prepareInputs(String userId, String sessionId, List<Long> attachmentIds,
                                           Long userMessageId);

    /**
     * 将本轮已绑定的图片附件读取为原生多模态内容；普通文档仍由沙箱工具处理。
     */
    List<ContentBlock> readInputImages(String userId, String sessionId, List<ConversationFileDo> attachments);

    /**
     * 将模型回复中的图片保存为本轮会话产物，重复结果按图片内容去重。
     */
    void publishImages(RuntimeContext context, Long userMessageId, Msg result);

    /**
     * 在 AgentScope 已启动当前沙箱后，将会话附件和已发布产物从 S3 恢复到工作区。
     *
     * @param context AgentScope 运行上下文
     */
    void materializeFiles(RuntimeContext context);

    /**
     * 查询本轮消息中已经发布的 Agent 产物。
     *
     * @param userId        用户 ID
     * @param sessionId     会话 ID
     * @param userMessageId 用户消息 ID
     * @return 按发布时间排列的文件
     */
    List<ConversationFileDo> listPublishedOutputs(String userId, String sessionId, Long userMessageId);

    /**
     * 批量查询指定用户多个会话中已经发布的 Agent 产物。
     *
     * @param userId     用户 ID
     * @param sessionIds 会话 ID 集合
     * @return 按会话 ID 分组、按发布时间排列的文件
     */
    Map<String, List<ConversationFileDo>> listPublishedOutputs(String userId, Collection<String> sessionIds);

    /**
     * 将 AgentScope 沙箱中生成的文件发布为可下载的会话产物。
     *
     * @param context       AgentScope 运行上下文
     * @param userMessageId 本轮用户消息 ID
     * @param sandboxPath   沙箱工作区相对路径
     * @param displayName   展示文件名
     * @return 已发布文件
     */
    ConversationFileDo publish(RuntimeContext context, Long userMessageId, String sandboxPath, String displayName);

    /**
     * 删除指定用户和会话的全部 S3 对象及元数据。
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     */
    void deleteConversationFiles(String userId, String sessionId);
}
