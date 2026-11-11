package com.x.ai.service.impl;

import cn.hutool.core.lang.Snowflake;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.x.ai.common.model.KnowledgeLinkParam;
import com.x.ai.db.entity.AgentKnowledgeLink;
import com.x.ai.db.entity.AgentKnowledgeLinkFolder;
import com.x.ai.db.service.AgentKnowledgeLinkFolderIService;
import com.x.ai.db.service.AgentKnowledgeLinkIService;
import com.x.ai.db.mapper.AgentKnowledgeLinkMapper;
import com.x.ai.db.service.impl.AgentKnowledgeLinkIServiceImpl;
import com.x.ai.service.KnowledgeLinkFolderManagementService;
import com.x.framework.common.exception.XException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KnowledgeLinkOwnershipTest {

    @BeforeAll
    static void initializeEntityMapping() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "knowledge-owner-test"),
                AgentKnowledgeLink.class);
    }

    @Test
    void catalogRestrictsTheDatabaseQueryToTheCaller() {
        AgentKnowledgeLinkMapper mapper = mock(AgentKnowledgeLinkMapper.class);
        AgentKnowledgeLinkIServiceImpl links = new AgentKnowledgeLinkIServiceImpl();
        ReflectionTestUtils.setField(links, "baseMapper", mapper);
        ReflectionTestUtils.setField(links, "entityClass", AgentKnowledgeLink.class);
        when(mapper.selectList(any())).thenReturn(List.of());
        KnowledgeLinkManagementServiceImpl service = new KnowledgeLinkManagementServiceImpl(links,
                new Snowflake(1, 1), mock(KnowledgeLinkFolderManagementService.class));

        assertThat(service.catalog("42")).isEmpty();

        ArgumentCaptor<LambdaQueryWrapper<AgentKnowledgeLink>> query = ArgumentCaptor.captor();
        verify(mapper).selectList(query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("owner_user_id =", "enabled =").doesNotContain(" OR ");
        assertThat(query.getValue().getParamNameValuePairs().values()).contains("42", true);
    }

    @Test
    void deletingAnotherUsersLinkCannotMatchTheDatabaseMutation() {
        AgentKnowledgeLinkMapper mapper = mock(AgentKnowledgeLinkMapper.class);
        AgentKnowledgeLinkIServiceImpl links = new AgentKnowledgeLinkIServiceImpl();
        ReflectionTestUtils.setField(links, "baseMapper", mapper);
        ReflectionTestUtils.setField(links, "entityClass", AgentKnowledgeLink.class);
        KnowledgeLinkManagementServiceImpl service = new KnowledgeLinkManagementServiceImpl(links,
                new Snowflake(1, 1), mock(KnowledgeLinkFolderManagementService.class));

        assertThrows(XException.class, () -> service.delete("42", 7L));

        ArgumentCaptor<LambdaUpdateWrapper<AgentKnowledgeLink>> query = ArgumentCaptor.captor();
        verify(mapper).delete(query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("id =", "owner_user_id =").doesNotContain(" OR ");
        assertThat(query.getValue().getParamNameValuePairs().values()).contains("42", 7L);
    }

    @Test
    void creatingLinkUsesCallerOwnershipAndLocksOwnedDestination() {
        AgentKnowledgeLinkIService links = mock(AgentKnowledgeLinkIService.class);
        KnowledgeLinkFolderManagementService folders = mock(KnowledgeLinkFolderManagementService.class);
        KnowledgeLinkManagementServiceImpl service = new KnowledgeLinkManagementServiceImpl(links,
                new Snowflake(1, 1), folders);
        when(links.save(any())).thenReturn(true);

        service.create("42", new KnowledgeLinkParam().setName(" 文档 ").setSummary(" 说明 ")
                .setUrl("https://example.com/guide").setFolderId(7L));

        ArgumentCaptor<AgentKnowledgeLink> captured = ArgumentCaptor.forClass(AgentKnowledgeLink.class);
        verify(links).save(captured.capture());
        assertEquals("42", captured.getValue().getOwnerUserId());
        assertEquals("文档", captured.getValue().getName());
        verify(folders).lockOwned("42", 7L);
    }

    @Test
    void rejectsNonHttpLinksAndEmbeddedCredentialsBeforePersistence() {
        AgentKnowledgeLinkIService links = mock(AgentKnowledgeLinkIService.class);
        KnowledgeLinkFolderManagementService folders = mock(KnowledgeLinkFolderManagementService.class);
        KnowledgeLinkManagementServiceImpl service = new KnowledgeLinkManagementServiceImpl(links,
                new Snowflake(1, 1), folders);
        for (String url : new String[]{"file:///etc/passwd", "https://user:password@example.com/guide"}) {
            assertThrows(XException.class, () -> service.create("42", new KnowledgeLinkParam()
                    .setName("文档").setSummary("说明").setUrl(url)));
        }
        verifyNoInteractions(links, folders);
    }

    @Test
    void missingOwnedFolderCannotFallBackToAnotherUserFolder() {
        AgentKnowledgeLinkFolderIService folders = mock(AgentKnowledgeLinkFolderIService.class);
        KnowledgeLinkFolderManagementServiceImpl service = new KnowledgeLinkFolderManagementServiceImpl(folders,
                mock(AgentKnowledgeLinkIService.class), new Snowflake(1, 1));

        assertThrows(XException.class, () -> service.lockOwned("42", 7L));

        verify(folders).findForUpdate(7L, "42");
    }

    @Test
    void rejectsMovingFolderUnderItsDescendant() {
        AgentKnowledgeLinkFolderIService folders = mock(AgentKnowledgeLinkFolderIService.class);
        KnowledgeLinkFolderManagementServiceImpl service = new KnowledgeLinkFolderManagementServiceImpl(folders,
                mock(AgentKnowledgeLinkIService.class), new Snowflake(1, 1));
        when(folders.findForUpdate(7L, "42"))
                .thenReturn(new AgentKnowledgeLinkFolder().setId(7L).setParentId(0L));
        when(folders.findForUpdate(8L, "42"))
                .thenReturn(new AgentKnowledgeLinkFolder().setId(8L).setParentId(7L));

        assertThrows(XException.class, () -> service.move("42", 7L, 8L));
    }
}
