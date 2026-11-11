package com.x.ai.service.impl;

import com.x.ai.common.constants.KnowledgeConstant;
import com.x.ai.common.domain.KnowledgeDocumentDo;
import com.x.ai.db.entity.KnowledgeDocument;
import com.x.ai.db.enums.KnowledgeDocumentStatus;
import com.x.ai.db.service.KnowledgeDocumentIService;
import com.x.ai.properties.KnowledgeEmbeddingProperties;
import com.x.ai.properties.KnowledgeProperties;
import com.x.ai.service.KnowledgeEmbeddingService;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class RagKnowledgeServiceImplTest {

    private final KnowledgeDocumentIService documents = mock(KnowledgeDocumentIService.class);

    private final KnowledgeEmbeddingService embeddings = mock(KnowledgeEmbeddingService.class);

    private final KnowledgeProperties properties = new KnowledgeProperties();

    private final KnowledgeEmbeddingProperties embeddingProperties = new KnowledgeEmbeddingProperties();

    private final RagKnowledgeServiceImpl service = new RagKnowledgeServiceImpl(documents, embeddings,
            properties, embeddingProperties);

    @BeforeEach
    void configureKnowledge() {
        properties.setMaxUploadSizeBytes(104_857_600L);
        properties.setChunkSize(800);
        properties.setChunkOverlap(120);
        properties.setMaxContentCharacters(2_000_000);
        properties.setMinScore(0.2);
        embeddingProperties.setBaseUrl("https://embedding.invalid");
        embeddingProperties.setModel("test-embedding");
        embeddingProperties.setDimensions(2);
        embeddingProperties.setBatchSize(16);
        embeddingProperties.setTimeoutSeconds(120);
    }

    @Test
    void vectorFailureKeepsOriginalDocumentAndExtractedPreviewForRetry() {
        byte[] source = "可预览的中文内容".getBytes(StandardCharsets.UTF_8);
        KnowledgeDocument document = new KnowledgeDocument().setOwnerId("42").setPath("guide.txt")
                .setName("guide.txt").setSource(source).setContentType("text/plain").setGeneration("version-1");
        when(documents.stage("42", "guide.txt", "guide.txt", "text/plain", source)).thenReturn(document);
        when(documents.find("42", "guide.txt", false)).thenReturn(document);
        when(documents.saveContent(eq(document), anyString())).thenAnswer(invocation -> {
            document.setContent(invocation.getArgument(1));
            return true;
        });
        when(embeddings.embed(any())).thenThrow(new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_INDEX_ERROR));
        doAnswer(invocation -> {
            document.setStatus(KnowledgeDocumentStatus.FAILED).setErrorMessage(invocation.getArgument(1));
            return null;
        }).when(documents).fail(eq(document), anyString());

        KnowledgeDocumentDo result = service.upload("42", "guide.txt", source);
        assertEquals(KnowledgeDocumentStatus.FAILED.getCode(), result.getStatus());
        assertEquals("可预览的中文内容", service.preview("42", "guide.txt").getContent());
        verify(documents, never()).publish(any(), anyString(), any(), any(), anyString(), anyInt());
    }

    @Test
    void supersededUploadDoesNotEmbedOrPublishTheOldVersion() {
        byte[] source = "old content".getBytes(StandardCharsets.UTF_8);
        KnowledgeDocument old = new KnowledgeDocument().setOwnerId("42").setPath("guide.txt")
                .setName("guide.txt").setSource(source).setGeneration("old");
        KnowledgeDocument current = new KnowledgeDocument().setOwnerId("42").setPath("guide.txt")
                .setName("guide.txt").setStatus(KnowledgeDocumentStatus.READY).setChunkCount(2);
        when(documents.stage("42", "guide.txt", "guide.txt", "text/plain", source)).thenReturn(old);
        when(documents.saveContent(old, "old content")).thenReturn(false);
        when(documents.find("42", "guide.txt", false)).thenReturn(current);

        assertEquals(2, service.upload("42", "guide.txt", source).getChunkCount());
        verifyNoInteractions(embeddings);
        verify(documents, never()).publish(any(), anyString(), any(), any(), anyString(), anyInt());
    }

    @Test
    void privateAccessUsesTheRequestedOwnerAtEveryReadBoundary() {
        assertThrows(XException.class, () -> service.preview("other-user", "private.txt"));
        assertThrows(XException.class, () -> service.download("other-user", "private.txt"));
        assertThrows(XException.class, () -> service.chunks("other-user", "private.txt", 1, 20));
        assertThrows(XException.class, () -> service.reindex("other-user", "private.txt"));
        assertThrows(XException.class, () -> service.delete("other-user", "private.txt"));
        verify(documents).find("other-user", "private.txt", true);
        verify(documents).restart("other-user", "private.txt");
        verify(documents).delete("other-user", "private.txt");
        verifyNoInteractions(embeddings);
    }

    @Test
    void semanticSearchOnlyReceivesTheCurrentUserAndExplicitlyIncludedPublicOwner() {
        List<Double> vector = List.of(1.0, 0.1);
        when(embeddings.embed(List.of("退款规则"))).thenReturn(List.of(vector));
        when(documents.search(any(), any(), anyString(), anyInt(), anyDouble(), anyInt())).thenReturn(List.of());
        service.search("42", "退款规则", 5, true);
        verify(documents).search(eq(List.of("42", KnowledgeConstant.PUBLIC_OWNER)), eq(vector),
                anyString(), anyInt(), anyDouble(), eq(5));
        service.search("42", "退款规则", 5, false);
        verify(documents).search(eq(List.of("42")), eq(vector), anyString(), anyInt(), anyDouble(), eq(5));
    }

    @Test
    void rejectsUnsafePathsInvalidPaginationAndUnsupportedFilesBeforePersistence() {
        for (String path : List.of("../other.txt", "/other.txt", "nested/../other.txt", "nested//other.txt", "C:/other.txt")) {
            assertThrows(XException.class, () -> service.download("42", path));
        }
        assertThrows(XException.class, () -> service.chunks("42", "valid.txt", 0, 20));
        assertThrows(XException.class, () -> service.chunks("42", "valid.txt", 1, 101));
        assertThrows(XException.class, () -> service.upload("42", "program.exe", new byte[]{1}));
        assertThrows(XException.class, () -> service.search("42", " ", 5, true));
        assertThrows(XException.class, () -> service.search("42", "退款规则", 21, true));
        verifyNoInteractions(documents, embeddings);
    }
}
