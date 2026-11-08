package com.x.ai.service;

public interface PublicResourceVersionService {

    /**
     * 获取公共 Skill/MCP 的集群版本。
     */
    long currentVersion();

    /**
     * 公共 Skill/MCP 变化后推进集群版本。
     */
    long advanceVersion();
}
