package com.x.ai.service.impl;

import com.x.ai.common.dto.UserSkillCreateDto;
import com.x.ai.common.model.KnowledgeLinkParam;
import com.x.ai.properties.AgentProperties;
import com.x.ai.service.AdministratorAuthorizationService;
import com.x.ai.service.HarnessAgentService;
import com.x.ai.service.KnowledgeLinkFolderManagementService;
import com.x.ai.service.KnowledgeLinkManagementService;
import com.x.ai.service.PublicResourceVersionService;
import com.x.ai.service.ResourceEnablementService;
import com.x.ai.skill.PublicSkillRepositoryFactory;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class PublicContentAuthorizationTest {

    private final AdministratorAuthorizationService authorization = mock(AdministratorAuthorizationService.class);

    @Test
    void ordinaryUserCannotWritePublicKnowledgeFile() {
        AgentProperties properties = mock(AgentProperties.class);
        PublicKnowledgeServiceImpl service = new PublicKnowledgeServiceImpl(authorization, properties);
        doThrow(new XException(ResponseCodeEnum.ADMIN_PERMISSION_REQUIRED))
                .when(authorization).requireAdministrator("42");

        assertThrows(XException.class, () -> service.writeText("42", "policy.md", "不能修改公共内容"));

        verifyNoInteractions(properties);
    }

    @Test
    void ordinaryUserCannotCreatePublicKnowledgeLink() {
        KnowledgeLinkManagementService links = mock(KnowledgeLinkManagementService.class);
        KnowledgeLinkFolderManagementService folders = mock(KnowledgeLinkFolderManagementService.class);
        PublicKnowledgeLinkServiceImpl service = new PublicKnowledgeLinkServiceImpl(authorization, links, folders);
        doThrow(new XException(ResponseCodeEnum.ADMIN_PERMISSION_REQUIRED))
                .when(authorization).requireAdministrator("42");

        assertThrows(XException.class, () -> service.createLink("42", new KnowledgeLinkParam()
                .setName("公共文档").setSummary("介绍").setUrl("https://example.com")));

        verifyNoInteractions(links, folders);
    }

    @Test
    void ordinaryUserCannotModifyPublicSkillOrEvictRuntimes() {
        PublicSkillRepositoryFactory repositories = mock(PublicSkillRepositoryFactory.class);
        HarnessAgentService agents = mock(HarnessAgentService.class);
        PublicResourceVersionService versions = mock(PublicResourceVersionService.class);
        PublicSkillServiceImpl service = new PublicSkillServiceImpl(authorization, repositories, agents, versions,
                mock(ResourceEnablementService.class));
        doThrow(new XException(ResponseCodeEnum.ADMIN_PERMISSION_REQUIRED))
                .when(authorization).requireAdministrator("42");

        assertThrows(XException.class, () -> service.create("42", new UserSkillCreateDto()
                .setName("public-skill").setDescription("描述").setContent("内容")));

        verifyNoInteractions(repositories, agents, versions);
    }
}
