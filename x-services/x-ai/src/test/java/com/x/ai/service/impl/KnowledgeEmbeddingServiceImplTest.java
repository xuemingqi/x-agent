package com.x.ai.service.impl;

import com.x.ai.client.OllamaEmbeddingClient;
import com.x.ai.common.model.OllamaEmbeddingRequest;
import com.x.ai.common.model.OllamaEmbeddingResponse;
import com.x.ai.properties.KnowledgeEmbeddingProperties;
import com.x.framework.common.exception.XException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeEmbeddingServiceImplTest {

    private final OllamaEmbeddingClient client = mock(OllamaEmbeddingClient.class);

    private final KnowledgeEmbeddingProperties properties = new KnowledgeEmbeddingProperties();

    @BeforeEach
    void configureEmbedding() {
        properties.setBaseUrl("https://embedding.invalid");
        properties.setModel("test-embedding");
        properties.setDimensions(2);
        properties.setBatchSize(16);
        properties.setTimeoutSeconds(120);
    }

    @Test
    void neverAllowsOllamaToSilentlyTruncateDocumentText() {
        when(client.embed(any())).thenReturn(new OllamaEmbeddingResponse().setEmbeddings(List.of(List.of(1.0, 0.0))));
        new KnowledgeEmbeddingServiceImpl(client, properties).embed(List.of("完整文档内容"));
        ArgumentCaptor<OllamaEmbeddingRequest> request = ArgumentCaptor.forClass(OllamaEmbeddingRequest.class);
        verify(client).embed(request.capture());
        assertFalse(request.getValue().isTruncate());
        assertEquals("test-embedding", request.getValue().getModel());
        assertEquals(List.of("完整文档内容"), request.getValue().getInput());
    }

    @Test
    void rejectsMismatchedCountsDimensionsInvalidNumbersAndZeroVectors() {
        KnowledgeEmbeddingServiceImpl service = new KnowledgeEmbeddingServiceImpl(client, properties);
        for (List<List<Double>> embeddings : List.of(List.<List<Double>>of(), List.of(List.of(1.0)),
                List.of(List.of(Double.NaN, 0.0)), List.of(List.of(0.0, 0.0)))) {
            when(client.embed(any())).thenReturn(new OllamaEmbeddingResponse().setEmbeddings(embeddings));
            assertThrows(XException.class, () -> service.embed(List.of("文档")));
        }
    }

    @Test
    void batchesRequestsAndKeepsInputOrder() {
        properties.setBatchSize(1);
        when(client.embed(any())).thenAnswer(invocation -> {
            OllamaEmbeddingRequest request = invocation.getArgument(0);
            return new OllamaEmbeddingResponse().setEmbeddings(List.of(
                    List.of(Double.parseDouble(request.getInput().getFirst()), 1.0)));
        });
        assertEquals(List.of(List.of(1.0, 1.0), List.of(2.0, 1.0)),
                new KnowledgeEmbeddingServiceImpl(client, properties).embed(List.of("1", "2")));
    }
}
