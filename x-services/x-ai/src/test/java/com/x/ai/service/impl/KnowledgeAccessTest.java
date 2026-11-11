package com.x.ai.service.impl;

import com.x.ai.service.AdministratorAuthorizationService;
import com.x.ai.service.RagKnowledgeService;
import com.x.framework.common.dto.UserInfo;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.UserThreadLocalUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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

        verify(documents).search("42", "报销规则", 5, false);
        verify(documents).preview("42", "policy.md");
    }

    @Test
    void ordinaryUserCannotReadOrReindexPublicDocumentsThroughAdministrationEndpoints() {
        AdministratorAuthorizationService authorization = mock(AdministratorAuthorizationService.class);
        RagKnowledgeService documents = mock(RagKnowledgeService.class);
        PublicKnowledgeServiceImpl service = new PublicKnowledgeServiceImpl(authorization, documents);
        doThrow(new XException(ResponseCodeEnum.ADMIN_PERMISSION_REQUIRED))
                .when(authorization).requireAdministrator("42");

        assertThrows(XException.class, () -> service.preview("42", "policy.md"));
        assertThrows(XException.class, () -> service.chunks("42", "policy.md", 1, 20));
        assertThrows(XException.class, () -> service.search("42", "报销规则", 5));
        assertThrows(XException.class, () -> service.reindex("42", "policy.md"));

        verifyNoInteractions(documents);
    }
}
