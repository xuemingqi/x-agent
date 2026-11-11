package com.x.ai.service;

import java.util.List;

public interface KnowledgeEmbeddingService {

    /**
     * 将文本按配置分批向量化，严格校验返回顺序、数量、维度和数值。
     *
     * @param texts 文本列表
     * @return 与文本一一对应的向量
     */
    List<List<Double>> embed(List<String> texts);
}
