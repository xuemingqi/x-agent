package com.x.ai.service.impl;

import com.x.ai.service.RagKnowledgeService;
import com.x.ai.common.model.ToolContext;
import com.x.ai.tool.KnowledgeSearchTools;
import com.x.framework.common.dto.UserInfo;
import com.x.framework.common.utils.UserThreadLocalUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class KnowledgeAccessTest {

    @AfterEach
    void clearUser() {
        UserThreadLocalUtil.clear();
    }

    @Test
    void personalSearchAndPreviewUseAuthenticatedIdentity() {
        UserThreadLocalUtil.set(UserInfo.builder().userId("42").build());
        RagKnowledgeService documents = mock(RagKnowledgeService.class);
        KnowledgeServiceImpl service = new KnowledgeServiceImpl(documents);

        service.search("报销规则", 5);
        service.preview("policy.md");

        verify(documents).search("42", "报销规则", 5);
        verify(documents).preview("42", "policy.md");
    }

    @Test
    void agentSearchUsesTheExecutionIdentityRatherThanAnUnrelatedRequestThread() {
        UserThreadLocalUtil.set(UserInfo.builder().userId("42").build());
        RagKnowledgeService documents = mock(RagKnowledgeService.class);

        new KnowledgeSearchTools(documents).search("报销规则", new ToolContext().setUserId("43"));

        verify(documents).search("43", "报销规则", 5);
    }

}
