package com.x.ai.common.model;

import lombok.Value;

import java.util.List;

/**
 * 同一次所有者查询得到的 MCP 配置及其指纹，避免建连时重复读取到不同版本。
 */
@Value
public class McpRuntimeSnapshot {

    /**
     * 当前用户启用的配置，按配置 ID 排序，后续建连只使用这份快照。
     */
    private List<McpServer> servers;

    /**
     * 包含凭证与超时配置的内容摘要，摘要不暴露凭证明文。
     */
    private String fingerprint;

    public McpRuntimeSnapshot(List<McpServer> servers, String fingerprint) {
        this.servers = List.copyOf(servers);
        this.fingerprint = fingerprint;
    }
}
