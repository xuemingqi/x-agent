package com.x.ai.db.service.impl;

import com.x.ai.config.KnowledgeVectorDatabase;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeDocumentIServiceImplTest {

    @Test
    void semanticRetrievalFiltersOwnershipBeforeComputingSimilarity() {
        KnowledgeVectorDatabase database = mock(KnowledgeVectorDatabase.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(database.jdbc()).thenReturn(jdbc);

        new KnowledgeDocumentIServiceImpl(database).search("42", List.of(1.0, 0.1), "test-model", 2, 0.2, 5);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), eq("42"), eq("test-model"), eq(2),
                eq("[1.0,0.1]"), eq("[1.0,0.1]"), eq(0.2), eq("[1.0,0.1]"), eq(5));
        assertThat(sql.getValue()).contains("WHERE c.owner_id = ?", "d.owner_id = c.owner_id", "d.status = 'READY'")
                .doesNotContain(" OR ", "owner_id IN");
    }
}
