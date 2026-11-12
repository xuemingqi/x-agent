package com.x.ai.service.impl;

import com.x.ai.service.RagKnowledgeService;
import com.x.ai.service.KnowledgeBaseService;
import com.x.ai.common.domain.KnowledgeBaseDo;
import java.util.List;
import com.x.ai.common.model.ToolContext;
import com.x.ai.tool.KnowledgeSearchTools;
import com.x.framework.common.dto.UserInfo;
import com.x.framework.common.utils.UserThreadLocalUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeAccessTest {

    @AfterEach
    void clearUser() {
        UserThreadLocalUtil.clear();
    }

    @Test
    void personalSearchAndPreviewUseAuthenticatedIdentity() {
        UserThreadLocalUtil.set(UserInfo.builder().userId("42").build());
        RagKnowledgeService documents = mock(RagKnowledgeService.class);
        KnowledgeBaseService bases = mock(KnowledgeBaseService.class);
        when(bases.requireOwned("42", List.of("base-1"))).thenReturn(List.of(new KnowledgeBaseDo().setId("base-1")));
        KnowledgeServiceImpl service = new KnowledgeServiceImpl(documents, bases);

        service.search("base-1", "报销规则", 5);
        service.preview("base-1", "policy.md");

        verify(documents).search("42", List.of("base-1"), "报销规则", 5);
        verify(documents).preview("42", "base-1", "policy.md");
    }

    @Test
    void agentSearchUsesTheExecutionIdentityRatherThanAnUnrelatedRequestThread() {
        UserThreadLocalUtil.set(UserInfo.builder().userId("42").build());
        RagKnowledgeService documents = mock(RagKnowledgeService.class);

        new KnowledgeSearchTools(documents).search("报销规则", new ToolContext().setUserId("43").setKnowledgeBaseIds(List.of("base-2")));

        verify(documents).search("43", List.of("base-2"), "报销规则", 5);
    }

}
