package com.x.ai.service.impl;

import com.x.ai.common.model.ResourceEnablement;
import com.x.ai.service.UserSkillService;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillRuntimeServiceImplTest {

    private final UserSkillService users = mock(UserSkillService.class);

    @Test
    void personalSnapshotUsesDatabaseFilteredOrderedSkillsWithoutFilteringAgain() {
        SkillRuntimeServiceImpl service = new SkillRuntimeServiceImpl(users);
        ResourceEnablement enabled = spy(new ResourceEnablement(Set.of()));
        AgentSkill first = AgentSkill.builder().name("alpha").description("First").skillContent("instructions").build();
        AgentSkill second = AgentSkill.builder().name("beta").description("Second").skillContent("instructions").build();
        when(users.listEnabled("42", enabled)).thenReturn(List.of(first, second));

        var snapshot = service.load("42", enabled);

        assertEquals(List.of("alpha", "beta"), snapshot.getRepositories().getLast().getAllSkillNames());
        verify(users).listEnabled("42", enabled);
        verify(enabled, never()).isSkillEnabled(anyString());
    }

    @Test
    void disabledOwnSkillHasNoFallbackRepository() {
        SkillRuntimeServiceImpl service = new SkillRuntimeServiceImpl(users);
        ResourceEnablement enabled = new ResourceEnablement(Set.of(ResourceEnablement.skillKey("example")));
        when(users.listEnabled("42", enabled)).thenReturn(List.of());

        var snapshot = service.load("42", enabled);

        assertEquals(1, snapshot.getRepositories().size());
        assertTrue(snapshot.getRepositories().getFirst().getAllSkills().isEmpty());
        assertTrue(snapshot.getRepositories().stream().noneMatch(AgentSkillRepository::isWriteable));
    }

    @Test
    void changingPersonalSkillContentChangesRuntimeFingerprint() {
        SkillRuntimeServiceImpl service = new SkillRuntimeServiceImpl(users);
        ResourceEnablement enabled = new ResourceEnablement(Set.of());
        AgentSkill skill = AgentSkill.builder().name("example").description("Example").skillContent("old").build();
        when(users.listEnabled("42", enabled)).thenReturn(List.of(skill), List.of(skill.toBuilder().skillContent("new").build()));

        assertNotEquals(service.load("42", enabled).getFingerprint(), service.load("42", enabled).getFingerprint());
    }
}
