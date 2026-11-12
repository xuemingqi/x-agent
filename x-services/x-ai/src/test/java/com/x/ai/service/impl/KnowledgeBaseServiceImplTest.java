package com.x.ai.service.impl;

import com.x.ai.common.domain.KnowledgeBaseDo;
import com.x.ai.common.dto.KnowledgeBaseDto;
import com.x.ai.db.entity.KnowledgeBase;
import com.x.ai.db.service.KnowledgeBaseIService;
import com.x.framework.common.dto.UserInfo;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.JsonUtil;
import com.x.framework.common.utils.UserThreadLocalUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeBaseServiceImplTest {

    private final KnowledgeBaseIService bases = mock(KnowledgeBaseIService.class);

    private final KnowledgeBaseServiceImpl service = new KnowledgeBaseServiceImpl(bases);

    @AfterEach
    void clearIdentity() {
        UserThreadLocalUtil.clear();
    }

    @Test
    void commonBaseAlwaysParticipatesAndStaleOrForeignSelectionsAreIgnored() {
        when(bases.findOwned("42", List.of("project", "removed", "foreign")))
                .thenReturn(List.of(new KnowledgeBase().setId("project").setOwnerId("42")));
        assertThat(service.searchableBaseIds("42", List.of("project", "removed", "foreign")))
                .containsExactly("default-42", "project");
        assertThat(service.searchableBaseIds("42", List.of())).containsExactly("default-42");
        verify(bases, org.mockito.Mockito.times(2)).ensureDefault("42");
    }

    @Test
    void unknownOrForeignBaseIsRejectedBeforeAssociation() {
        when(bases.findOwned("42", List.of("project", "foreign")))
                .thenReturn(List.of(new KnowledgeBase().setId("project")));
        assertThrows(XException.class, () -> service.requireOwned("42", List.of("project", "foreign")));
    }

    @Test
    void commonKnowledgeBaseCannotBeDeleted() {
        UserThreadLocalUtil.set(UserInfo.builder().userId("42").build());
        when(bases.findOwned("42", List.of("default-42")))
                .thenReturn(List.of(new KnowledgeBase().setId("default-42").setDefault(true)));
        assertThrows(XException.class, () -> service.delete("default-42"));
        verify(bases, never()).delete("42", "default-42");
    }

    @Test
    void invalidNameIsRejectedBeforeStorage() {
        assertThrows(XException.class, () -> service.create(new KnowledgeBaseDto().setName(" ")));
    }

    @Test
    void defaultMarkerHasTheFrontendJsonFieldName() {
        String json = JsonUtil.toJsonStrOrThrow(new KnowledgeBaseDo().setId("default-42").setDefault(true));
        assertThat(json).contains("\"isDefault\":true").doesNotContain("\"default\":");
    }
}
