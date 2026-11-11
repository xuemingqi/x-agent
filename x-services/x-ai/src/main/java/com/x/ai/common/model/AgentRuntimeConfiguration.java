package com.x.ai.common.model;

import com.x.ai.db.enums.AgentModelMultimodalCapability;
import lombok.Builder;
import lombok.Value;

/**
 * Agent 构建配置的不可变快照，值相等即允许复用缓存，不包含凭证明文及可变模型对象。
 */
@Value
@Builder
public class AgentRuntimeConfiguration {

    /**
     * 模型完整运行配置的不可逆摘要，包含密钥变化但不保存明文。
     */
    private String modelFingerprint;

    /**
     * 显式比较能力，保证同一秒内修改模型配置也能使缓存失效。
     */
    private AgentModelMultimodalCapability multimodalCapability;

    /**
     * 当前用户 MCP 配置的不可逆摘要，使其他节点上的配置变更也能刷新运行时。
     */
    private String mcpFingerprint;

    /**
     * 构建本次运行时所使用的用户扩展启用状态。
     */
    private ResourceEnablement enablement;

    /**
     * 已授权 Skill 内容摘要，正文或附件变化后需要重新构建 Agent。
     */
    private String skillFingerprint;
}
