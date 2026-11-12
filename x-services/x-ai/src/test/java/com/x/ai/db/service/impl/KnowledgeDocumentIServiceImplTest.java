package com.x.ai.db.service.impl;

import com.x.ai.config.KnowledgeVectorDatabase;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KnowledgeDocumentIServiceImplTest {

    @Test
    void semanticRetrievalBindsOwnerAndSelectedKnowledgeBasesBeforeComputingSimilarity() throws Exception {
        KnowledgeVectorDatabase database = mock(KnowledgeVectorDatabase.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(database.jdbc()).thenReturn(jdbc);

        new KnowledgeDocumentIServiceImpl(database).search("42", List.of("base-1", "base-2"),
                List.of(1.0, 0.1), "test-model", 2, 0.2, 5);

        ArgumentCaptor<PreparedStatementCreator> creator = ArgumentCaptor.forClass(PreparedStatementCreator.class);
        verify(jdbc).query(creator.capture(), any(RowMapper.class));
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(any(String.class))).thenReturn(statement);
        creator.getValue().createPreparedStatement(connection);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        assertThat(sql.getValue()).contains("WHERE c.owner_id = ?", "c.knowledge_base_id IN (?, ?)",
                        "d.knowledge_base_id = c.knowledge_base_id", "d.status = 'READY'")
                .doesNotContain(" OR ", "owner_id IN");
        verify(statement).setString(1, "42");
        verify(statement).setString(2, "base-1");
        verify(statement).setString(3, "base-2");
    }

    @Test
    void emptySelectionNeverQueriesTheDatabase() {
        KnowledgeVectorDatabase database = mock(KnowledgeVectorDatabase.class);
        KnowledgeDocumentIServiceImpl service = new KnowledgeDocumentIServiceImpl(database);
        assertThat(service.search("42", List.of(), List.of(1.0, 0.1), "test-model", 2, 0.2, 5)).isEmpty();
        assertThat(service.hasSearchableChunks("42", List.of(), "test-model", 2)).isFalse();
        verifyNoInteractions(database);
    }
}
