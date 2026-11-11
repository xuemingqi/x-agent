package com.x.ai.service.impl;

import com.x.ai.client.OllamaEmbeddingClient;
import com.x.ai.common.model.OllamaEmbeddingRequest;
import com.x.ai.common.model.OllamaEmbeddingResponse;
import com.x.ai.properties.KnowledgeEmbeddingProperties;
import com.x.ai.service.KnowledgeEmbeddingService;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeEmbeddingServiceImpl implements KnowledgeEmbeddingService {

    private final OllamaEmbeddingClient client;

    private final KnowledgeEmbeddingProperties properties;

    @Override
    public List<List<Double>> embed(List<String> texts) {
        List<List<Double>> result = new ArrayList<>(texts.size());
        for (int offset = 0; offset < texts.size(); offset += properties.getBatchSize()) {
            List<String> batch = texts.subList(offset, Math.min(texts.size(), offset + properties.getBatchSize()));
            try {
                OllamaEmbeddingResponse response = client.embed(new OllamaEmbeddingRequest()
                        .setModel(properties.getModel()).setInput(batch).setTruncate(false));
                if (response == null || response.getEmbeddings() == null
                        || response.getEmbeddings().size() != batch.size()) {
                    throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_INDEX_ERROR);
                }
                response.getEmbeddings().forEach(this::validateVector);
                result.addAll(response.getEmbeddings());
            } catch (FeignException error) {
                log.warn("Knowledge embedding request failed, model={}, status={}", properties.getModel(), error.status());
                throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_INDEX_ERROR);
            }
        }
        return result;
    }

    /**
     * 拒绝维度漂移、非有限数与零向量，避免写入不可检索的索引。
     */
    private void validateVector(List<Double> vector) {
        if (vector == null || vector.size() != properties.getDimensions()
                || vector.stream().anyMatch(value -> value == null || !Double.isFinite(value) || !Float.isFinite(value.floatValue()))
                || vector.stream().noneMatch(value -> value.floatValue() != 0)) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_INDEX_ERROR);
        }
    }
}
