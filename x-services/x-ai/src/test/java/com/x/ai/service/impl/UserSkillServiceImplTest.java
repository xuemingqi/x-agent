package com.x.ai.service.impl;

import cn.hutool.core.lang.Snowflake;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.x.ai.db.entity.AgentUserSkill;
import com.x.ai.db.entity.AgentUserSkillResource;
import com.x.ai.db.service.AgentUserSkillIService;
import com.x.ai.db.service.AgentUserSkillResourceIService;
import io.agentscope.core.skill.AgentSkill;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserSkillServiceImplTest {

    @Test
    void deletionScopesParentByOwnerAndReliesOnForeignKeyForAttachments() {
        AgentUserSkillIService definitions = mock(AgentUserSkillIService.class);
        AgentUserSkillResourceIService resources = mock(AgentUserSkillResourceIService.class);
        UserSkillServiceImpl service = new UserSkillServiceImpl(definitions, resources, new Snowflake(1, 1),
                mock(Validator.class));
        LambdaQueryChainWrapper<AgentUserSkill> query = mock(RETURNS_SELF);
        LambdaUpdateChainWrapper<AgentUserSkill> update = mock(RETURNS_SELF);
        when(definitions.lambdaQuery()).thenReturn(query);
        when(definitions.lambdaUpdate()).thenReturn(update);
        when(query.eq(any(), any())).thenReturn(query);
        when(update.eq(any(), any())).thenReturn(update);
        when(query.one()).thenReturn(new AgentUserSkill().setId(7L).setUserId("42").setName("example"));
        when(update.remove()).thenReturn(true);

        assertTrue(service.delete("42", "example"));

        verify(query).eq(any(), eq("42"));
        verify(update).eq(any(), eq("42"));
        verify(update).eq(any(), eq(7L));
        verify(update).remove();
        verifyNoInteractions(resources);
    }

    @Test
    void readingPersonalSkillQueriesOnlyAuthenticatedOwner() {
        AgentUserSkillIService definitions = mock(AgentUserSkillIService.class);
        AgentUserSkillResourceIService resources = mock(AgentUserSkillResourceIService.class);
        UserSkillServiceImpl service = new UserSkillServiceImpl(definitions, resources, new Snowflake(1, 1),
                mock(Validator.class));
        LambdaQueryChainWrapper<AgentUserSkill> definitionQuery = mock(RETURNS_SELF);
        LambdaQueryChainWrapper<AgentUserSkillResource> resourceQuery = mock(RETURNS_SELF);
        when(definitions.lambdaQuery()).thenReturn(definitionQuery);
        when(resources.lambdaQuery()).thenReturn(resourceQuery);
        when(definitionQuery.eq(any(), any())).thenReturn(definitionQuery);
        when(resourceQuery.in(any(), org.mockito.ArgumentMatchers.<java.util.Collection<?>>any())).thenReturn(resourceQuery);
        when(resourceQuery.list()).thenReturn(List.of());
        when(definitionQuery.one()).thenReturn(new AgentUserSkill().setId(7L).setUserId("42")
                .setName("example").setDescription("database description").setSkillContent("new content")
                .setMetadata(Map.of("name", "example", "description", "database description")));
        AgentSkill current = service.get("42", "example");

        assertEquals("new content", current.getSkillContent());
        verify(definitions, never()).saveOrUpdate(any(AgentUserSkill.class));
        verify(definitionQuery).eq(any(), eq("42"));
    }
}
