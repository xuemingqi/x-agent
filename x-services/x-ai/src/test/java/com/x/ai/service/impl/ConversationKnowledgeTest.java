package com.x.ai.service.impl;

import cn.hutool.core.lang.Snowflake;
import com.x.ai.common.domain.KnowledgeBaseDo;
import com.x.ai.db.entity.AgentConversation;
import com.x.ai.db.service.AgentConversationIService;
import com.x.ai.db.service.AgentConversationMessageIService;
import com.x.ai.service.AgentScopeStoreService;
import com.x.ai.service.ConversationFileService;
import com.x.ai.service.KnowledgeBaseService;
import com.x.framework.common.dto.UserInfo;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.UserThreadLocalUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationKnowledgeTest {

    private final AgentConversationIService conversations = mock(AgentConversationIService.class);

    private final KnowledgeBaseService knowledgeBases = mock(KnowledgeBaseService.class);

    private final ConversationServiceImpl service = new ConversationServiceImpl(conversations,
            mock(AgentConversationMessageIService.class), mock(AgentScopeStoreService.class),
            mock(ConversationFileService.class), knowledgeBases, mock(Snowflake.class));

    @BeforeEach
    void setUser() {
        UserThreadLocalUtil.set(UserInfo.builder().userId("42").build());
    }

    @AfterEach
    void clearUser() {
        UserThreadLocalUtil.clear();
    }

    @Test
    void creatingAConversationPersistsSelectedBasesAndKeepsCommonImplicit() {
        List<String> requested = List.of("common", "project", "project");
        when(knowledgeBases.requireOwned("42", requested)).thenReturn(List.of(
                new KnowledgeBaseDo().setId("common").setDefault(true),
                new KnowledgeBaseDo().setId("project"), new KnowledgeBaseDo().setId("project")));

        assertThat(service.create(requested).getKnowledgeBaseIds()).containsExactly("project");

        ArgumentCaptor<AgentConversation> saved = ArgumentCaptor.forClass(AgentConversation.class);
        verify(conversations).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo("42");
        assertThat(saved.getValue().getKnowledgeBaseIds()).containsExactly("project");
    }

    @Test
    void rejectsForeignKnowledgeBasesBeforeSavingConversation() {
        when(knowledgeBases.requireOwned("42", List.of("foreign")))
                .thenThrow(new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_NOT_FOUND));

        assertThatThrownBy(() -> service.create(List.of("foreign"))).isInstanceOf(XException.class);

        verify(conversations, never()).save(any());
    }

    @Test
    void replacingSelectionCanClearAllExtraBasesWithoutChangingTheTitle() {
        when(conversations.getById("session")).thenReturn(new AgentConversation()
                .setSessionId("session").setUserId("42").setTitle("项目讨论").setKnowledgeBaseIds(List.of("old")));
        when(knowledgeBases.requireOwned("42", List.of())).thenReturn(List.of());

        assertThat(service.updateKnowledgeBases("session", List.of()).getKnowledgeBaseIds()).isEmpty();

        ArgumentCaptor<AgentConversation> saved = ArgumentCaptor.forClass(AgentConversation.class);
        verify(conversations).updateById(saved.capture());
        assertThat(saved.getValue().getKnowledgeBaseIds()).isEmpty();
        assertThat(saved.getValue().getTitle()).isNull();
    }

    @Test
    void selectionIsRestoredOnlyForItsOwnerAndForeignConversationsCannotBeEdited() {
        when(conversations.getById("session")).thenReturn(new AgentConversation()
                .setSessionId("session").setUserId("43").setKnowledgeBaseIds(List.of("private")));

        assertThat(service.knowledgeBaseIds("42", "session")).isEmpty();
        assertThatThrownBy(() -> service.updateKnowledgeBases("session", List.of())).isInstanceOf(XException.class);
        verify(conversations, never()).updateById(any(AgentConversation.class));

        assertThat(service.knowledgeBaseIds("43", "session")).containsExactly("private");
        assertThat(service.knowledgeBaseIds("42", "missing")).isEmpty();
    }
}
