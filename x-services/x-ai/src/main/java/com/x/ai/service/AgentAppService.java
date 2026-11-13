package com.x.ai.service;

import com.x.ai.common.domain.AgentAppDetailDo;
import com.x.ai.common.domain.AgentAppDo;
import com.x.ai.common.domain.AgentAppVersionDo;
import com.x.ai.common.dto.AgentAppSaveDto;

import java.util.List;

public interface AgentAppService {

    /**
     * 查询当前登录用户的个人应用。
     */
    List<AgentAppDo> list();

    /**
     * 查询指定可信用户的个人应用，供 Agent 工具调用。
     */
    List<AgentAppDo> list(String userId);

    /**
     * 读取当前用户的动态应用及当前发布包，拒绝其他用户的应用。
     */
    AgentAppDetailDo get(String id);

    /**
     * 读取指定可信用户的动态应用及当前发布包。
     */
    AgentAppDetailDo get(String userId, String id);

    /**
     * 为当前用户创建个人应用并发布第一个不可变版本。
     */
    AgentAppDetailDo create(AgentAppSaveDto request);

    /**
     * 为指定可信用户创建个人应用并发布第一个不可变版本。
     */
    AgentAppDetailDo create(String userId, AgentAppSaveDto request);

    /**
     * 发布当前用户的新版本；baseVersion 必须仍是当前版本。
     */
    AgentAppDetailDo update(String id, AgentAppSaveDto request);

    /**
     * 发布指定可信用户的新版本；并发修改冲突时不覆盖已发布内容。
     */
    AgentAppDetailDo update(String userId, String id, AgentAppSaveDto request);

    /**
     * 列出当前用户应用的不可变版本。
     */
    List<AgentAppVersionDo> versions(String id);

    /**
     * 列出指定可信用户应用的不可变版本。
     */
    List<AgentAppVersionDo> versions(String userId, String id);

    /**
     * 为当前用户激活历史版本，切换前校验 baseVersion 并验证历史对象完整性。
     */
    AgentAppDetailDo activate(String id, String version, String baseVersion);

    /**
     * 为指定可信用户激活历史版本，拒绝跨应用版本和并发覆盖。
     */
    AgentAppDetailDo activate(String userId, String id, String version, String baseVersion);

    /**
     * 删除当前用户的动态应用及对象；对象删除失败保留隐藏的引用供原 ID 重试。
     */
    boolean delete(String id);

    /**
     * 删除指定可信用户的动态应用及对象，失败时保留对象引用以供重试。
     */
    boolean delete(String userId, String id);
}
